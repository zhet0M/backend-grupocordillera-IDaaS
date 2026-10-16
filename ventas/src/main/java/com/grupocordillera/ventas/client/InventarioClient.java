package com.grupocordillera.ventas.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "inventario", url = "${app.services.inventario-url:http://localhost:8083/inventario}")
public interface InventarioClient {

    @GetMapping("/sku/{sku}")
    Map<String, Object> porSku(@PathVariable("sku") String sku);

    @PutMapping("/descontar/{sku}/{cantidad}")
    void descontarStock(@PathVariable("sku") String sku, @PathVariable("cantidad") int cantidad);
}
