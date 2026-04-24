package com.codesync.execution.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for execution job queue.
 *
 * Flow:
 * 1. REST endpoint receives job → saves to DB as QUEUED
 * 2. Job ID published to RabbitMQ queue
 * 3. ExecutionWorker consumes from queue
 * 4. Worker runs code → updates DB with result
 *
 * Queue:    execution.jobs
 * Exchange: execution.exchange
 * Key:      execution.route
 *
 * Dead letter queue for failed jobs:
 * Queue:    execution.jobs.dlq
 */
@Configuration
public class RabbitMQConfig {

    @Value("${execution.sandbox.queue-name:execution.jobs}")
    private String queueName;

    @Value("${execution.sandbox.exchange-name:execution.exchange}")
    private String exchangeName;

    @Value("${execution.sandbox.routing-key:execution.route}")
    private String routingKey;

    // ── Main Queue ────────────────────────────────────────────────────
    @Bean
    public Queue executionQueue() {
        return QueueBuilder.durable(queueName)
                // Route failed messages to DLQ
                .withArgument("x-dead-letter-exchange",
                        exchangeName + ".dlx")
                .withArgument("x-dead-letter-routing-key",
                        routingKey + ".dlq")
                // Max 1000 messages in queue
                .withArgument("x-max-length", 1000)
                .build();
    }

    // ── Dead Letter Queue ─────────────────────────────────────────────
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    // ── Exchange ──────────────────────────────────────────────────────
    @Bean
    public DirectExchange executionExchange() {
        return new DirectExchange(exchangeName);
    }

    // ── Dead Letter Exchange ──────────────────────────────────────────
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(exchangeName + ".dlx");
    }

    // ── Bindings ──────────────────────────────────────────────────────
    @Bean
    public Binding executionBinding() {
        return BindingBuilder
                .bind(executionQueue())
                .to(executionExchange())
                .with(routingKey);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(routingKey + ".dlq");
    }

    // ── Message Converter ─────────────────────────────────────────────
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ── RabbitTemplate ────────────────────────────────────────────────
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory) {
        RabbitTemplate template =
                new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}