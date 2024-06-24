package com.hmall.api.client;

import com.hmall.api.client.fallback.OrderClientFallBack;
import com.hmall.api.config.DefaultFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "trade-service",
        configuration = DefaultFeignConfig.class,
        fallbackFactory = OrderClientFallBack.class
)
public interface OrderClient {
    @PutMapping("/orders/{orderId}")
    void markOrderPaySuccess(@PathVariable("orderId") Long orderId);
}
