package com.hmall.gateway.filter;

import cn.hutool.core.collection.CollUtil;
import com.hmall.common.exception.UnauthorizedException;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtTool jwtTool;

    private final AuthProperties authProperties;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 1. 判断路径是否需要校验
        if (isExclude(request.getPath().value())) {
            // 如果不需要校验就直接跳过
            return chain.filter(exchange);
        }

        // 2. 获取请求头中的 token
        List<String> tokens = request.getHeaders().get("authorization");
        // 3. 校验token
        String token = null;
        if (!CollUtil.isEmpty(tokens)) {
            token = tokens.get(0);
        }

        // 注意这里的异常处理
        Long userId = null;
        try {
            userId = jwtTool.parseToken(token);
        } catch (UnauthorizedException e) {
            // 4.1 校验失败返回错误信息
            ServerHttpResponse response = exchange.getResponse();
            response.setRawStatusCode(401);
            return response.setComplete();
        }

        // 4.2 校验成功放行
        // 把 userId 存放在 user-info 这个 header 中
        String userInfo = userId.toString();
        ServerWebExchange ex = exchange.mutate().request(b -> b.header("user-info", userInfo)).build();

        return chain.filter(ex);
    }

    /**
     * 判断路径是否在 excludePaths 中，是就返回true
     * @param antPath 请求路径
     * @return 是否在 excludePaths 中
     */
    private boolean isExclude(String antPath) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if(antPathMatcher.match(pathPattern, antPath)){
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
