package com.hmall.api.client.fallback;

import com.hmall.api.client.OrderClient;
import com.hmall.common.exception.BizIllegalException;
import org.springframework.cloud.openfeign.FallbackFactory;

public class OrderClientFallBack implements FallbackFactory<OrderClient> {
    @Override
    public OrderClient create(Throwable cause) {
        return new OrderClient() {
            @Override
            public void markOrderPaySuccess(Long orderId) {
                throw new BizIllegalException("远程调用markOrderPaySuccess异常", cause);
            }
        };
    }
}
