package com.grupocordillera.alertas.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queues.alertas}")
    private String queueName;

    @Value("${app.rabbitmq.queues.alertas-dlq}")
    private String dlqQueueName;

    @Value("${app.rabbitmq.exchanges.cordillera-exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.exchanges.dlx}")
    private String dlxExchangeName;

    @Value("${app.rabbitmq.routing-keys.venta-registrada}")
    private String routingKey;

    @Value("${app.rabbitmq.routing-keys.dlq}")
    private String dlqRoutingKey;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 1. Topic Exchange principal del negocio
    @Bean
    public TopicExchange cordilleraExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // 2. Dead Letter Exchange (DLX) directo
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    // 3. Cola DLQ donde aterrizan los mensajes con error
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqQueueName).build();
    }

    // 4. Cola Principal de Alertas configurada con argumentos para derivar a DLQ
    @Bean
    public Queue alertasQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlxExchangeName);
        args.put("x-dead-letter-routing-key", dlqRoutingKey);
        return new Queue(queueName, true, false, false, args);
    }

    // 5. Binding de la Cola Principal al Topic Exchange con su routing key
    @Bean
    public Binding bindingAlertasQueue(Queue alertasQueue, TopicExchange cordilleraExchange) {
        return BindingBuilder.bind(alertasQueue).to(cordilleraExchange).with(routingKey);
    }

    // 6. Binding de la Cola DLQ al Dead Letter Exchange
    @Bean
    public Binding bindingDlq(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(dlqRoutingKey);
    }

    // 7. Factory con modo MANUAL de confirmación (ACK)
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
