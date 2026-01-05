package com.khistory.gateway.filter;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ResponseBodyLoggingFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    
                    return super.writeWith(fluxBody.map(dataBuffer -> {
                        // Body 읽기
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);
                        
                        String responseBody = new String(content, StandardCharsets.UTF_8);
                        
                        // Django 응답 로그
                        log.info("=== Django Response Body ===");
                        log.info("Path: {}", exchange.getRequest().getPath());
                        log.info("Body: {}", responseBody);
                        log.info("============================");
                        
                        return originalResponse.bufferFactory().wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };
        
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
    
    @Override
    public int getOrder() {
        return -1; // 높은 우선순위
    }
}