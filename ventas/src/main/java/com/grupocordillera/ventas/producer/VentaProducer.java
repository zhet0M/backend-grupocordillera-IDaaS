package com.grupocordillera.ventas.producer;

import com.grupocordillera.ventas.dto.event.VentaRegistradaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VentaProducer {

    private static final Logger log = LoggerFactory.getLogger(VentaProducer.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchanges.cordillera-exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-keys.venta-registrada}")
    private String routingKey;

    public VentaProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarVentaRegistrada(VentaRegistradaEvent event) {
        log.info("Publicando evento asincrono en RabbitMQ: Venta ID {}, Total {}", event.ventaId(), event.total());
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}