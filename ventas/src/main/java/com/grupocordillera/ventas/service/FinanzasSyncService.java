package com.grupocordillera.ventas.service;

import com.grupocordillera.ventas.client.FinanzasClient;
import com.grupocordillera.ventas.dto.MovimientoFinancieroRequest;
import com.grupocordillera.ventas.model.Venta;
import com.grupocordillera.ventas.repository.VentasRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class FinanzasSyncService {

    private static final int MAX_INTENTOS_FINANZAS = 5;

    private final FinanzasClient finanzasClient;
    private final VentasRepository ventasRepository;

    // Constructor explícito para evitar incompatibilidades con el compilador de VS Code
    public FinanzasSyncService(FinanzasClient finanzasClient, VentasRepository ventasRepository) {
        this.finanzasClient = finanzasClient;
        this.ventasRepository = ventasRepository;
    }

    @CircuitBreaker(name = "finanzas", fallbackMethod = "fallbackSincronizarFinanzas")
    public Venta sincronizarVentaConFinanzas(Venta venta) {
        finanzasClient.registrarMovimiento(construirMovimientoFinanciero(venta));
        venta.setEstadoFinanzas(Venta.EstadoFinanzas.SINCRONIZADO);
        venta.setFechaUltimoIntentoFinanzas(LocalDateTime.now());
        venta.setUltimoErrorFinanzas(null);
        return ventasRepository.save(venta);
    }

    public Venta fallbackSincronizarFinanzas(Venta venta, Throwable t) {
        int intentosActuales = venta.getIntentosFinanzas() == null ? 0 : venta.getIntentosFinanzas();
        venta.setIntentosFinanzas(intentosActuales + 1);
        venta.setFechaUltimoIntentoFinanzas(LocalDateTime.now());
        venta.setUltimoErrorFinanzas(extraerMensaje(t));
        venta.setEstadoFinanzas(
                venta.getIntentosFinanzas() >= MAX_INTENTOS_FINANZAS
                        ? Venta.EstadoFinanzas.ERROR
                        : Venta.EstadoFinanzas.PENDIENTE
        );
        return ventasRepository.save(venta);
    }

    private MovimientoFinancieroRequest construirMovimientoFinanciero(Venta venta) {
        MovimientoFinancieroRequest request = new MovimientoFinancieroRequest();
        request.setVentaId(venta.getId());
        request.setProductoId(venta.getProductoId());
        request.setSkuProducto(venta.getSkuProducto());
        request.setNombreProducto(venta.getNombreProducto());
        request.setCantidad(venta.getCantidad());
        request.setIngresos(BigDecimal.valueOf(venta.getMontoTotal()));
        request.setFechaRegistro(venta.getFecha());
        request.setSucursal(venta.getSucursal());
        request.setTipoMovimiento(MovimientoFinancieroRequest.TipoMovimiento.INGRESO);
        return request;
    }

    private String extraerMensaje(Throwable t) {
        Throwable actual = t;
        String ultimoMensaje = null;

        while (actual != null) {
            if (actual.getMessage() != null && !actual.getMessage().isBlank()) {
                ultimoMensaje = actual.getMessage();
            }
            actual = actual.getCause();
        }

        return ultimoMensaje != null ? ultimoMensaje : "sin detalle";
    }
}