package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.client.OrderClient;
import com.hmall.api.client.PayClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.RabbitMqHelper;
import com.hmall.common.utils.UserContext;
import com.hmall.api.dto.OrderFormDTO;
import com.hmall.trade.constant.MQConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final RabbitTemplate rabbitTemplate;
    private final PayClient payClient;
    private RabbitMqHelper rabbitMqHelper;

    @Override
    @GlobalTransactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        log.info("开始处理", System.currentTimeMillis());
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
//        cartClient.deleteCartItemByIds(itemIds);
        // mq
        // todo 抽取带用户信息的
        try {
            rabbitTemplate.convertAndSend("trade.topic", "order.create", itemIds, new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) throws AmqpException {
                    message.getMessageProperties().setHeader("user-info", UserContext.getUser());
                    return message;
                }
            });
        } catch (Exception e) {
            log.error("创建订单成功的消息发送失败！ 需要清理的商品id {}", itemIds, e); // log.error 默认会处理最后一个e
        }

        // 4.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        // 5. 发送延迟消息，校验订单支付状态
//        rabbitTemplate.convertAndSend(MQConstants.DELAY_EXCHANGE_NAME, MQConstants.DELAY_ORDER_KEY, order.getId(), message -> {
//            message.getMessageProperties().setDelay(10000);
//            return message;
//        });
        // 改为使用工具类
        rabbitMqHelper.sendDelayMessage(MQConstants.DELAY_EXCHANGE_NAME, MQConstants.DELAY_ORDER_KEY, order.getId(), 10000);

        log.info("处理完毕，订单创建成功！", System.currentTimeMillis());
        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        // UPDATE `order` SET status = ? , pay_time = ? WHERE id = ? AND status = 1
        lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
    }

    @Override
    @GlobalTransactional
    public void cancelOrder(Long orderId) {
        // 1. 取消订单
        // UPDATE `order` SET status = ? WHERE id = ? AND status = 1
        lambdaUpdate()
                .set(Order::getStatus, 3)
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();

        // 2. 恢复库存
        // 2.1. 查询订单详情
        List<OrderDetail> details = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId)
                .list();
        // 2.2. 恢复库存
        List<OrderDetailDTO> detailDTOS = details.stream()
                .map(detail -> {
                    OrderDetailDTO dto = new OrderDetailDTO();
                    dto.setItemId(detail.getItemId());
                    dto.setNum(detail.getNum());
                    return dto;
                })
                .collect(Collectors.toList());
        try {
            itemClient.restoreStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 3. 将支付单状态改为超时未支付
        try {
            payClient.tryCancelPay(orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
