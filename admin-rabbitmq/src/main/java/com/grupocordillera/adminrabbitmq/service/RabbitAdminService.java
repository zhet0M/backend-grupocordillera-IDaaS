package com.grupocordillera.adminrabbitmq.service;

import com.grupocordillera.adminrabbitmq.dto.BindingRequestDTO;
import com.grupocordillera.adminrabbitmq.dto.ExchangeRequestDTO;
import com.grupocordillera.adminrabbitmq.dto.QueueRequestDTO;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RabbitAdminService {

    // AmqpAdmin es la interfaz de Spring que ejecuta las órdenes contra RabbitMQ
    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    // 1. Crear una cola con o sin Dead Letter Queue configurada
    public void createQueue(QueueRequestDTO dto) {
        Map<String, Object> args = new HashMap<>();
        if (dto.deadLetterExchange() != null && !dto.deadLetterExchange().isBlank()) {
            args.put("x-dead-letter-exchange", dto.deadLetterExchange().trim());
        }
        if (dto.deadLetterRoutingKey() != null && !dto.deadLetterRoutingKey().isBlank()) {
            args.put("x-dead-letter-routing-key", dto.deadLetterRoutingKey().trim());
        }

        Queue queue = new Queue(
            dto.name().trim(),
            dto.durable(),
            dto.exclusive(),
            dto.autoDelete(),
            args.isEmpty() ? null : args
        );
        amqpAdmin.declareQueue(queue);
    }

    // 2. Eliminar una cola por su nombre
    public boolean deleteQueue(String queueName) {
        return amqpAdmin.deleteQueue(queueName.trim());
    }

    // 3. Crear un Exchange dinámico (TOPIC, FANOUT o DIRECT)
    public void createExchange(ExchangeRequestDTO dto) {
        Exchange exchange = switch (dto.type().toUpperCase()) {
            case "TOPIC" -> new TopicExchange(dto.name().trim(), dto.durable(), dto.autoDelete());
            case "FANOUT" -> new FanoutExchange(dto.name().trim(), dto.durable(), dto.autoDelete());
            default -> new DirectExchange(dto.name().trim(), dto.durable(), dto.autoDelete());
        };
        amqpAdmin.declareExchange(exchange);
    }

    // 4. Eliminar un Exchange por su nombre
    public boolean deleteExchange(String exchangeName) {
        return amqpAdmin.deleteExchange(exchangeName.trim());
    }

    // 5. Vincular una cola a un Exchange mediante un Routing Key (Binding)
    public void createBinding(BindingRequestDTO dto) {
        Binding binding = new Binding(
            dto.queueName().trim(),
            Binding.DestinationType.QUEUE,
            dto.exchangeName().trim(),
            dto.routingKey().trim(),
            null
        );
        amqpAdmin.declareBinding(binding);
    }

    // 6. Eliminar el enlace entre la cola y el Exchange
    public void deleteBinding(BindingRequestDTO dto) {
        Binding binding = new Binding(
            dto.queueName().trim(),
            Binding.DestinationType.QUEUE,
            dto.exchangeName().trim(),
            dto.routingKey().trim(),
            null
        );
        amqpAdmin.removeBinding(binding);
    }
}
