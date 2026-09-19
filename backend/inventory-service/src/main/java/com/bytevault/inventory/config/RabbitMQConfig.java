package com.bytevault.inventory.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";

    public static final String QUEUE_ORDER_CANCELLED = "inventory.queue.order.cancelled";
    public static final String QUEUE_ORDER_PAID = "inventory.queue.order.paid";
    public static final String QUEUE_PAYMENT_FAILED = "inventory.queue.payment.failed";

    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_ORDER_PAID = "order.paid";
    public static final String ROUTING_KEY_PAYMENT_FAILED = "payment.failed";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CANCELLED).build();
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_PAID).build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_FAILED).build();
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderExchange).with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(orderExchange).with(ROUTING_KEY_ORDER_PAID);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(paymentExchange).with(ROUTING_KEY_PAYMENT_FAILED);
    }
}
