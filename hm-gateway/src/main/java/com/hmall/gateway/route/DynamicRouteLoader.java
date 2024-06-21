package com.hmall.gateway.route;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.hmall.common.utils.CollUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicRouteLoader {

    private final NacosConfigManager nacosConfigManager;
    private final RouteDefinitionWriter routeDefinitionWriter;

    // 路由配置文件的id和分组
    private final String dataId = "gateway-routes.json";
    private final String group = "DEFAULT_GROUP";

    private final Set<String> routeIds = new HashSet<>();

    @PostConstruct
    public void loadDynamicRoute() {
        try {
            // 从Nacos配置中心获取路由配置文件
            String configInfo = nacosConfigManager.getConfigService().getConfigAndSignListener(dataId, group, 5000, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String s) {
                    // 更新配置
                    updateConfigInfo(s);
                }
            });
            // 首次加载
            updateConfigInfo(configInfo);
        } catch (Exception e) {
            log.error("loadDynamicRoute error", e);
        }
    }

    public void updateConfigInfo(String configContent){
        // 1. 反序列化
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configContent, RouteDefinition.class);
        // 2. 清除旧路由
        routeIds.forEach(routeId -> {
            routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        });
        routeIds.clear();
        // 3. 更新路由
        if (CollUtils.isEmpty(routeDefinitions)) {
            return;
        }
        routeDefinitions.forEach(routeDefinition -> {
            routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
            routeIds.add(routeDefinition.getId());
        });
    }
}
