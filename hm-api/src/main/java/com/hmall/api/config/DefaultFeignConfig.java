package com.hmall.api.config;

import com.hmall.api.client.fallback.*;
import com.hmall.common.utils.UserContext;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLogLevel(){
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor requestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                // 获取用户信息
                Long userId = UserContext.getUser();
                if (userId != null) {
                    // 将用户信息添加到请求头中
                    requestTemplate.header("user-info", userId.toString());
                }
            }
        };
    }

    @Bean
    public ItemClientFallback ItemClientFallback(){
        return new ItemClientFallback();
    }

    @Bean
    public OrderClientFallBack OrderClientFallBack(){
        return new OrderClientFallBack();
    }

    @Bean
    public CartClientFallback CartClientFallback(){
        return new CartClientFallback();
    }

    @Bean
    public UserClientFallback UserClientFallback(){
        return new UserClientFallback();
    }

    @Bean
    public PayClientFallback PayClientFallback(){
        return new PayClientFallback();
    }
}
