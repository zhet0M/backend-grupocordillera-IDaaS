package com.grupocordillera.adminrabbitmq.controller;

import com.grupocordillera.adminrabbitmq.dto.BindingRequestDTO;
import com.grupocordillera.adminrabbitmq.dto.ExchangeRequestDTO;
import com.grupocordillera.adminrabbitmq.dto.QueueRequestDTO;
import com.grupocordillera.adminrabbitmq.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/rabbitmq")
@CrossOrigin(origins = "*")
public class RabbitAdminController {

    private final RabbitAdminService adminService;

    public RabbitAdminController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    // 1. Crear una cola (POST /api/v1/admin/rabbitmq/queues)
    @PostMapping("/queues")
    public ResponseEntity<Map<String, String>> createQueue(@Valid @RequestBody QueueRequestDTO request) {
        adminService.createQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Cola creada exitosamente", "queue", request.name()));
    }

    // 2. Eliminar una cola por nombre (DELETE /api/v1/admin/rabbitmq/queues/{name})
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Map<String, String>> deleteQueue(@PathVariable String name) {
        boolean deleted = adminService.deleteQueue(name);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Cola eliminada exitosamente", "queue", name));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Cola no encontrada o ya eliminada", "queue", name));
    }

    // 3. Crear un exchange (POST /api/v1/admin/rabbitmq/exchanges)
    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> createExchange(@Valid @RequestBody ExchangeRequestDTO request) {
        adminService.createExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Exchange creado exitosamente", "exchange", request.name()));
    }

    // 4. Eliminar un exchange por nombre (DELETE /api/v1/admin/rabbitmq/exchanges/{name})
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Map<String, String>> deleteExchange(@PathVariable String name) {
        boolean deleted = adminService.deleteExchange(name);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Exchange eliminado exitosamente", "exchange", name));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Exchange no encontrado o ya eliminado", "exchange", name));
    }

    // 5. Crear un binding entre cola y exchange (POST /api/v1/admin/rabbitmq/bindings)
    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> createBinding(@Valid @RequestBody BindingRequestDTO request) {
        adminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Binding creado exitosamente"));
    }

    // 6. Eliminar un binding (DELETE /api/v1/admin/rabbitmq/bindings)
    @DeleteMapping("/bindings")
    public ResponseEntity<Map<String, String>> deleteBinding(@Valid @RequestBody BindingRequestDTO request) {
        adminService.deleteBinding(request);
        return ResponseEntity.ok(Map.of("message", "Binding eliminado exitosamente"));
    }
}