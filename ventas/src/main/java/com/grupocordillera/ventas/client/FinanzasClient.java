package com.grupocordillera.ventas.client;

import com.grupocordillera.ventas.dto.MovimientoFinancieroRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "finanzas", url = "${app.services.finanzas-url:http://localhost:8084/finanzas}")
public interface FinanzasClient {

    @PostMapping
    void registrarMovimiento(@RequestBody MovimientoFinancieroRequest request);
}
