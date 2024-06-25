package com.hmall.api.client;

import com.hmall.api.client.fallback.PayClientFallback;
import com.hmall.api.config.DefaultFeignConfig;
import com.hmall.api.dto.PayOrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "pay-service",
        configuration = DefaultFeignConfig.class,
        fallbackFactory = PayClientFallback.class
)
public interface PayClient {

    @GetMapping("/pay-orders/biz/{id}")
    PayOrderDTO queryPayOrderByBizOrderNo(@PathVariable("id") Long id);

    @PostMapping("/pay-orders/cancel/{id}")
    void tryCancelPay(@PathVariable("id") Long id);
}
