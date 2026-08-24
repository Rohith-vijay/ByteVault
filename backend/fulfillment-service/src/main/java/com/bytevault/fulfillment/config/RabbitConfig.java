package com.bytevault.fulfillment.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "order.exchange";
    public static final String PAID_QUEUE = "order.queue.paid";
    public static final String ROUTING_KEY = "order.paid";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue paidQueue() {
        return QueueBuilder.durable(PAID_QUEUE).build();
    }

    @Bean
    public Binding paidBinding(Queue paidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(paidQueue).to(orderExchange).with(ROUTING_KEY);
    }
}
