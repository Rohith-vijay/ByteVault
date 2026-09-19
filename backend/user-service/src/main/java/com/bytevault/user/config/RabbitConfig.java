package com.bytevault.user.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "user.exchange";
    public static final String REGISTER_QUEUE = "user.queue.registration";
    public static final String ROUTING_KEY = "user.registered";

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable(REGISTER_QUEUE).build();
    }

    @Bean
    public Binding userRegisterBinding(Queue userRegisterQueue, TopicExchange userExchange) {
        return BindingBuilder.bind(userRegisterQueue).to(userExchange).with(ROUTING_KEY);
    }
}
