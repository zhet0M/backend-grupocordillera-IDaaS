package com.grupocordillera.adminrabbitmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExchangeRequestDTO(
    @NotBlank(message = "El nombre del exchange no puede estar vacio")
    @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Solo se permiten letras, numeros, puntos y guiones")
    String name,

    @NotBlank(message = "El tipo de exchange es obligatorio")
    @Pattern(regexp = "^(DIRECT|TOPIC|FANOUT)$", message = "Valores permitidos: DIRECT, TOPIC, FANOUT")
    String type,

    boolean durable,
    boolean autoDelete
) {}