package com.bytevault.order.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "order.exchange";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE);
    }
}
