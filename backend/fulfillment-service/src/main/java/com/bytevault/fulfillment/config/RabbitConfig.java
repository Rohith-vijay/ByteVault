package com.bytevault.fulfillment.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "order.exchange";
    public static final String PAID_QUEUE = "order.queue.paid";
    public static final String ROUTING_KEY = "order.paid";

    public static final String DLX_EXCHANGE = "order.exchange.dlx";
    public static final String DLQ_QUEUE = "order.queue.paid.dlq";
    public static final String DLQ_ROUTING_KEY = "order.paid.dlq";

    public static final String RETRY_QUEUE = "order.queue.paid.retry";
    public static final String RETRY_ROUTING_KEY = "order.paid.retry";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", 5000) // 5s backoff
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding(Queue retryQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(retryQueue).to(orderExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Queue paidQueue() {
        return QueueBuilder.durable(PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding paidBinding(Queue paidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(paidQueue).to(orderExchange).with(ROUTING_KEY);
    }
}
