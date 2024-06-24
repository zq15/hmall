package com.hmall.cart.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.cart.service.ICartService;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static org.springframework.amqp.core.ExchangeTypes.TOPIC;

@Component
@RequiredArgsConstructor
public class OrderStatusListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusListener.class);
    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.topic", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = TOPIC),
            key = "order.create"
    ))
    public void handleOrderCreate(Message message) {
        MessageProperties messageProperties = message.getMessageProperties();
        log.info("收到消息: {}", messageProperties);
        Long userId = (Long) messageProperties.getHeader("user-info");
        log.info("读取到的 userId: {}", userId);
        if (userId==null) {
            log.error("未获取到用户信息： {}", message.getMessageProperties().getMessageId());
        }
        UserContext.setUser(userId);
        byte[] body = message.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        Collection<Long> orderIds = null;
        try {
            orderIds = objectMapper.readValue(body, Collection.class);
            log.info("读取到的 orderIds: {}", orderIds);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cartService.removeByItemIds(orderIds);
    }
}
