package com.khistory.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestPath = exchange.getRequest().getPath().value();
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long endTime = System.currentTimeMillis();
            int statusCode = exchange.getResponse().getStatusCode() != null 
                ? exchange.getResponse().getStatusCode().value() 
                : 0;
                
            log.info("=== SCG Response ===");
            log.info("Path: {}", requestPath);
            log.info("Status Code: {}", statusCode);
            log.info("Duration: {}ms", (endTime - startTime));
            log.info("Response Headers: {}", exchange.getResponse().getHeaders());
            log.info("==================");
        }));
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // 마지막에 실행
    }
}