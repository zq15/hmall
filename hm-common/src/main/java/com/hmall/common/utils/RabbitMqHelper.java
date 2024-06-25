package com.hmall.common.utils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.UUID;

@Slf4j
@AllArgsConstructor
public class RabbitMqHelper {
    private final RabbitTemplate rabbitTemplate;

    public void sendMessage(String exchange, String routingKey, Object msg){
        log.info("准备发送消息");
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }

    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay){
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, message -> {
            message.getMessageProperties().setDelay(delay);
            return message;
        });
    }

    public void sendMessageWithConfirm(String exchange, String routingKey, Object msg, int maxRetries){
        // 1. 创建 CorrelationData
        CorrelationData cd = new CorrelationData(UUID.randomUUID().toString());
        // 2. 给 future 添加 ConfirmCallback
        cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
            int retryCount;
            @Override
            public void onFailure(Throwable ex) {
                // 2.1 Future 异常 基本不会出现
                log.error("send message fail", ex);
            }

            @Override
            public void onSuccess(CorrelationData.Confirm result) {
                // 不断重试
                if (result!=null && !result.isAck()) {
                    log.error("发送消息失败，收到nack，重试次数: {}", retryCount);
                    if (retryCount >= maxRetries) {
                        log.error("重试次数耗尽，发送失败");
                        return;
                    }
                    // 继续重试
                    rabbitTemplate.convertAndSend(exchange, routingKey, msg);
                    retryCount++;
                }
            }
        });
        // 3. 发送消息
        rabbitTemplate.convertAndSend("hmall.direct", "q", "hello", cd);
    }
}
