package com.grupocordillera.ventas.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grupocordillera.ventas.model.Venta;
import com.grupocordillera.ventas.service.VentasService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/ventas")
@Tag(name = "Ventas", description = "Registro, consulta y reproceso de ventas")
public class VentasController {

    private final VentasService ventasService;

    // Constructor explícito sin Lombok para evitar errores con el compilador de VS Code
    public VentasController(VentasService ventasService) {
        this.ventasService = ventasService;
    }

    @PostMapping
    public ResponseEntity<Venta> registrar(@RequestBody Venta venta) {
        return ResponseEntity.ok(ventasService.registrarVenta(venta));
    }

    @GetMapping
    public ResponseEntity<List<Venta>> obtenerTodas() {
        return ResponseEntity.ok(ventasService.obtenerTodas());
    }

    @GetMapping("/finanzas/pendientes")
    public ResponseEntity<List<Venta>> pendientesFinanzas() {
        return ResponseEntity.ok(ventasService.obtenerVentasPendientesFinanzas());
    }

    @PostMapping("/finanzas/reprocesar")
    public ResponseEntity<String> reprocesarFinanzas() {
        int reprocesadas = ventasService.reprocesarVentasPendientes();
        return ResponseEntity.ok("Ventas reprocesadas hacia finanzas: " + reprocesadas);
    }

    @GetMapping("/fecha/{fecha}")
    public ResponseEntity<List<Venta>> porFecha(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha) {
        return ResponseEntity.ok(ventasService.obtenerPorFecha(fecha));
    }

    @GetMapping("/hoy")
    public ResponseEntity<List<Venta>> hoy() {
        return ResponseEntity.ok(ventasService.obtenerHoy());
    }

    @GetMapping("/sucursal/{sucursal}")
    public ResponseEntity<List<Venta>> porSucursal(
            @PathVariable String sucursal) {
        return ResponseEntity.ok(ventasService.obtenerPorSucursal(sucursal));
    }

    @GetMapping("/rango")
    public ResponseEntity<List<Venta>> porRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin) {
        return ResponseEntity.ok(ventasService.obtenerPorRango(inicio, fin));
    }

    @GetMapping("/total/periodo")
    public ResponseEntity<Double> totalPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin) {
        return ResponseEntity.ok(ventasService.totalPorPeriodo(inicio, fin));
    }

    @GetMapping("/total/sucursal/{sucursal}")
    public ResponseEntity<Double> totalSucursal(
            @PathVariable String sucursal) {
        return ResponseEntity.ok(ventasService.totalPorSucursal(sucursal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ventasService.eliminarVenta(id);
        return ResponseEntity.noContent().build();
    }
}