package com.grupocordillera.api_gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/kpis")
@Tag(name = "KPIs Gateway", description = "Proxy del gateway hacia el servicio de KPIs")
public class KpisProxyController {

    private final RestTemplate restTemplate;
    private final String kpisUrl;

    public KpisProxyController(
            RestTemplate restTemplate,
            @Value("${kpis.url:http://ms_kpis:8086}") String kpisUrl) {
        this.restTemplate = restTemplate;
        this.kpisUrl = kpisUrl;
    }

    @GetMapping
    public ResponseEntity<String> obtenerResumen() {
        return forwardToKpis("/kpis");
    }

    @GetMapping("/{tipo}")
    public ResponseEntity<String> obtenerPorTipo(@PathVariable String tipo) {
        return forwardToKpis("/kpis/" + tipo);
    }

    /**
     * Reenvía la petición al microservicio de KPIs, propagando explícitamente
     * el header {@code Authorization} del token de Entra ID.
     *
     * <p>El Gateway MVC de Spring Cloud (rutas por application.properties) ya
     * propaga headers por defecto; este proxy manual necesita hacerlo a mano.</p>
     */
    private ResponseEntity<String> forwardToKpis(String path) {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attrs = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest incoming = attrs.getRequest();
            String auth = incoming.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && !auth.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, auth);
            }
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);
        return restTemplate.exchange(
                kpisUrl + path,
                HttpMethod.GET,
                request,
                String.class
        );
    }
}
