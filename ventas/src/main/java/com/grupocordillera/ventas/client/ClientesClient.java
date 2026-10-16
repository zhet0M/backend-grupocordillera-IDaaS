package com.grupocordillera.ventas.client;

import com.grupocordillera.ventas.dto.ClienteDTO;
import com.grupocordillera.ventas.dto.RegistrarCompraClienteRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "clientes", url = "${app.services.clientes-url:http://localhost:8085/clientes}")
public interface ClientesClient {

    @GetMapping("/{id}")
    ClienteDTO obtenerPorId(@PathVariable("id") Long id);

    @PutMapping("/{id}/compras")
    ClienteDTO registrarCompra(@PathVariable("id") Long id, @RequestBody RegistrarCompraClienteRequest request);
}
