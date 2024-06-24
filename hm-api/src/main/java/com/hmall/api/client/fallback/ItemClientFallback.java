package com.hmall.api.client.fallback;

import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collection;
import java.util.List;

@Slf4j
public class ItemClientFallback implements FallbackFactory<ItemClient> {
    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                log.error("远程调用queryItemByIds方法异常，参数: {}, 异常: {}", ids, cause);
                // 查询购物车允许失败，查询失败，返回空集合
                return List.of();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                // 库存扣减业务需要触发事务回滚，查询失败，返回异常
                throw new BizIllegalException(cause);
            }
        };
    }

    public static void main(String[] args) {
        log.error("远程调用queryItemByIds方法异常，参数: {}, 异常: {}", 1, "空指针异常");
    }
}
