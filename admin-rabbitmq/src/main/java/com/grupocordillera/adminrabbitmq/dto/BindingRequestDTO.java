package com.grupocordillera.adminrabbitmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BindingRequestDTO(
    @NotBlank(message = "El nombre de la cola es obligatorio")
    String queueName,

    @NotBlank(message = "El nombre del exchange es obligatorio")
    String exchangeName,

    @NotBlank(message = "El routing key es obligatorio")
    @Pattern(regexp = "^[a-zA-Z0-9._*#-]+$", message = "Routing key contiene caracteres invalidos")
    String routingKey
) {}