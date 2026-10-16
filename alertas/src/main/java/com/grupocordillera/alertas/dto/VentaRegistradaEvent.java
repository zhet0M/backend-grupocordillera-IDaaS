package com.grupocordillera.alertas.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaRegistradaEvent(
    Long ventaId,
    Long clienteId,
    BigDecimal total,
    String sucursal,
    LocalDateTime fecha
) implements Serializable {}
