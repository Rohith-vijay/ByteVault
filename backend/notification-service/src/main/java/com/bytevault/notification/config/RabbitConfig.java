package com.bytevault.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_PAID_QUEUE = "notification.queue.order.paid";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    public static final String USER_EXCHANGE = "user.exchange";
    public static final String USER_REGISTERED_QUEUE = "notification.queue.user.registered";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String PASSWORD_RESET_QUEUE = "notification.queue.password.reset";
    public static final String PASSWORD_RESET_ROUTING_KEY = "auth.password.reset";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderPaidNotificationQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE).build();
    }

    @Bean
    public Binding orderPaidNotificationBinding(Queue orderPaidNotificationQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidNotificationQueue).to(orderExchange).with(ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE);
    }

    @Bean
    public Queue userRegisteredNotificationQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE).build();
    }

    @Bean
    public Binding userRegisteredNotificationBinding(Queue userRegisteredNotificationQueue, TopicExchange userExchange) {
        return BindingBuilder.bind(userRegisteredNotificationQueue).to(userExchange).with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Queue passwordResetNotificationQueue() {
        return QueueBuilder.durable(PASSWORD_RESET_QUEUE).build();
    }

    @Bean
    public Binding passwordResetNotificationBinding(Queue passwordResetNotificationQueue, TopicExchange userExchange) {
        return BindingBuilder.bind(passwordResetNotificationQueue).to(userExchange).with(PASSWORD_RESET_ROUTING_KEY);
    }
}
