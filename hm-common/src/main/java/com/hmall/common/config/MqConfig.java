package com.hmall.common.config;

import cn.hutool.core.util.ObjectUtil;
import com.hmall.common.utils.UserContext;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class MqConfig {

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                Long userId = message.getMessageProperties().getHeader("user-info");
                if (ObjectUtil.isNotEmpty(userId)){
                    UserContext.setUser(userId);
                }
                return super.fromMessage(message);
            }
        };
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }
}
