package com.grupocordillera.api_gateway.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuración transversal del Gateway: CORS y RestTemplate para el proxy de KPIs.
 *
 * <p>NOTA Etapa 1: el antiguo filtro manual de JWT ({@code JwtAuthFilter}) queda
 * DESACTIVADO. La validación de tokens ahora la hace exclusivamente el filtro de
 * Spring Security OAuth2 Resource Server declarado en {@link ResourceServerConfig}.
 */
@Configuration
public class GatewayConfig {

    @Value("${cors.allowed-origins}")
    private String corsAllowedOriginsCsv;

    // =========================================================================
    // ── FILTRO JWT LEGACY (DESACTIVADO en Etapa 1) ───────────────────────────
    // =========================================================================
    //
    // @Bean
    // public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtAuthFilter jwtAuthFilter) {
    //     FilterRegistrationBean<JwtAuthFilter> bean =
    //             new FilterRegistrationBean<>();
    //     bean.setFilter(jwtAuthFilter);
    //     bean.addUrlPatterns("/*");
    //     bean.setOrder(2);
    //     return bean;
    // }

    // =========================================================================
    // ── CORS configurable por property ───────────────────────────────────────
    // =========================================================================
    /**
     * CORS centralizado en el Gateway. Orígenes permitidos se leen desde la
     * propiedad {@code cors.allowed-origins} (lista separada por comas). Esto
     * evita hardcodear URLs y permite sobreescribir via env var CORS_ALLOWED_ORIGINS.
     *
     * <p>Orden 1 para ejecutarse antes que el filtro de Spring Security OAuth2
     * Resource Server (etapa 2 agrega DedupeResponseHeader para evitar duplicados
     * con cualquier cabecera que agreguen microservicios internos).</p>
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.stream(corsAllowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        // ── Listas explícitas (NO wildcards) ──────────────────────────────────
        // Decisión 6: métodos HTTP autorizados. Ningún otro método (HEAD,
        // TRACE, CONNECT, PATCH adicionales, etc.) pasa el preflight.
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        // Headers permitidos en la REQUEST entrante.
        // Authorization (Bearer token) y Content-Type (JSON body) son los
        // únicos que el frontend Angular usa. Si requieres uno nuevo en el
        // futuro (ej: X-Request-ID), añádelo EXPLÍCITAMENTE aquí.
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE
        ));
        // Headers visibles para el código JavaScript del cliente (fetch/xhr).
        // Sin este ajuste el frontend solo puede leer Content-Type y Cache-Control.
        config.setExposedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.LOCATION,
                "Content-Disposition"
        ));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean =
                new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(1);
        return bean;
    }

    // RestTemplate para proxy de KPIs (KpisProxyController).
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // =========================================================================
    // ── Deduplicación de headers CORS ─────────────────────────────────────────
    // =========================================================================
    /**
     * Elimina ocurrencias duplicadas de {@code Access-Control-Allow-Origin} y
     * {@code Access-Control-Allow-Credentials} en la respuesta.
     *
     * <p>Sin este filtro, si un microservicio interno agrega por accidente estos
     * headers, el navegador recibe 2 valores iguales y rechaza el preflight con
     * "The 'Access-Control-Allow-Origin' header contains multiple values".</p>
     *
     * <p>Orden 2: después de {@link #corsFilter()} (orden 1) y antes de Spring
     * Security OAuth2 Resource Server (orden ~100-1000).</p>
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> corsDedupeFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {

            private static final List<String> DEDUPE_HEADERS = List.of(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS
            );

            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                filterChain.doFilter(request, response);

                // Despues de que la respuesta fue generada: dedup CORS headers.
                for (String header : DEDUPE_HEADERS) {
                    var values = response.getHeaders(header);
                    if (values != null && values.size() > 1) {
                        // Tomamos el PRIMER valor (el del Gateway) y borramos el resto.
                        String first = values.iterator().next();
                        response.setHeader(header, first);
                    }
                }
            }
        };

        FilterRegistrationBean<OncePerRequestFilter> bean =
                new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(2);
        return bean;
    }
}
