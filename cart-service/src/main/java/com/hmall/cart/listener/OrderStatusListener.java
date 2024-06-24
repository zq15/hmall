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

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.topic", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = TOPIC),
            key = "order.create"
    ))
    public void handleOrderCreate(Collection<Long> orderIds) {
        cartService.removeByItemIds(orderIds);
    }
}
