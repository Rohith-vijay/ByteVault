package com.bytevault.shipping.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String SHIPPING_EXCHANGE = "shipping.exchange";

    public static final String ORDER_PAID_QUEUE = "shipping.order.paid.queue";
    public static final String ORDER_CANCELLED_QUEUE = "shipping.order.cancelled.queue";

    public static final String ROUTING_KEY_ORDER_PAID = "order.paid";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_SHIPMENT_CREATED = "shipment.created";
    public static final String ROUTING_KEY_SHIPMENT_DISPATCHED = "shipment.dispatched";
    public static final String ROUTING_KEY_SHIPMENT_DELIVERED = "shipment.delivered";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange shippingExchange() {
        return new TopicExchange(SHIPPING_EXCHANGE);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(orderExchange).with(ROUTING_KEY_ORDER_PAID);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderExchange).with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
