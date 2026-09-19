package com.bytevault.warehouse.config;

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
    public static final String WAREHOUSE_EXCHANGE = "warehouse.exchange";

    public static final String ORDER_PAID_QUEUE = "warehouse.order.paid.queue";
    public static final String SHIPMENT_CREATED_QUEUE = "warehouse.shipment.created.queue";

    public static final String ROUTING_KEY_ORDER_PAID = "order.paid";
    public static final String ROUTING_KEY_SHIPMENT_CREATED = "shipment.created";
    public static final String ROUTING_KEY_PICKLIST_CREATED = "picklist.created";
    public static final String ROUTING_KEY_PICKLIST_PACKED = "picklist.packed";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange shippingExchange() {
        return new TopicExchange(SHIPPING_EXCHANGE);
    }

    @Bean
    public TopicExchange warehouseExchange() {
        return new TopicExchange(WAREHOUSE_EXCHANGE);
    }

    @Bean
    public Queue warehouseOrderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE, true);
    }

    @Bean
    public Queue warehouseShipmentCreatedQueue() {
        return new Queue(SHIPMENT_CREATED_QUEUE, true);
    }

    @Bean
    public Binding warehouseOrderPaidBinding(Queue warehouseOrderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(warehouseOrderPaidQueue).to(orderExchange).with(ROUTING_KEY_ORDER_PAID);
    }

    @Bean
    public Binding warehouseShipmentCreatedBinding(Queue warehouseShipmentCreatedQueue, TopicExchange shippingExchange) {
        return BindingBuilder.bind(warehouseShipmentCreatedQueue).to(shippingExchange).with(ROUTING_KEY_SHIPMENT_CREATED);
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
