package com.grupocordillera.alertas.consumer;

import com.grupocordillera.alertas.dto.CrearAlertaRequest;
import com.grupocordillera.alertas.dto.VentaRegistradaEvent;
import com.grupocordillera.alertas.model.Alerta;
import com.grupocordillera.alertas.service.AlertaService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class VentaConsumer {

    private static final Logger log = LoggerFactory.getLogger(VentaConsumer.class);
    private static final BigDecimal UMBRAL_ALERTA = new BigDecimal("50000");

    private final AlertaService alertaService;

    public VentaConsumer(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.alertas}", containerFactory = "rabbitListenerContainerFactory")
    public void consumirVenta(
            @Payload VentaRegistradaEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Mensaje recibido de RabbitMQ. DeliveryTag: {}, Venta ID: {}, Monto: ${}",
                deliveryTag, event.ventaId(), event.total());

        try {
            // 1. Validación defensiva
            if (event.total() == null || event.total().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Monto inválido para procesar alerta: " + event.total());
            }

            // 2. Regla de negocio de alertas
            if (event.total().compareTo(UMBRAL_ALERTA) >= 0) {
                log.warn(">>> [ALERTA DE SEGURIDAD] Venta de alto valor detectada: ID={}, Monto=${}, Sucursal={} <<<",
                        event.ventaId(), event.total(), event.sucursal());

                // Persistir en base de datos
                CrearAlertaRequest req = new CrearAlertaRequest();
                req.setCodigo("VENTA:" + event.ventaId());
                req.setTipo(Alerta.TipoAlerta.INFORMACION);
                req.setTitulo("Venta de alto valor detectada");
                req.setDetalle("Venta ID " + event.ventaId() + " por $" + event.total() + " en " + event.sucursal());
                alertaService.crearAlerta(req);
            } else {
                log.info("Venta ID={} procesada sin requerir notificación especial", event.ventaId());
            }

            // 3. Confirmación manual exitosa (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("ACK manual emitido exitosamente para DeliveryTag: {}", deliveryTag);

        } catch (Exception ex) {
            log.error("Error al procesar venta ID: {}. Derivando a DLQ. Causa: {}", event.ventaId(), ex.getMessage());

            // 4. Rechazo con requeue = false -> RabbitMQ lo transfiere a la Dead Letter Queue
            channel.basicNack(deliveryTag, false, false);
        }
    }
}