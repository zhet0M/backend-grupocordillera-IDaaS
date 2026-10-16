package com.grupocordillera.adminrabbitmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QueueRequestDTO(
    @NotBlank(message = "El nombre de la cola no puede estar vacio")
    @Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Solo se permiten letras, numeros, puntos, guiones y guiones bajos")
    String name,

    boolean durable,
    boolean exclusive,
    boolean autoDelete,
    String deadLetterExchange,
    String deadLetterRoutingKey
) {}