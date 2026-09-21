package com.grupocordillera.api_gateway.filter;

import java.io.IOException;     
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.grupocordillera.api_gateway.config.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * @deprecated OBSOLETO (Etapa 1 - Microsoft Entra ID).
 * Este filtro manual de JWT (HMAC JJWT propio) fue reemplazado por el
 * {@code SecurityFilterChain} en {@code ResourceServerConfig}, que actúa como
 * OAuth2 Resource Server validando tokens de Entra ID via JWKS + Nimbus.
 *
 * Se eliminó @Component para que Spring NO lo registre automaticamente.
 * La clase se conserva solo como referencia historica del flujo anterior.
 */
@Deprecated
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // Rutas públicas que no necesitan token
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/auth/login",
            "/auth/registro",
            "/swagger-ui.html",
            "/swagger-ui/",
            "/swagger-ui",
            "/v3/api-docs",
            "/v3/api-docs/",
            "/webjars/"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Si es ruta pública, dejar pasar
        if (RUTAS_PUBLICAS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Obtener el header Authorization
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Verifica que venga el token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token no proporcionado");
            return;
        }

        // Extrae y valida el token
        String token = authHeader.substring(7);

        if (!jwtService.validarToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token inválido o expirado");
            return;
        }

        // Token válido, agrega info del usuario al header
        String email = jwtService.extraerEmail(token);
        String rol = jwtService.extraerRol(token);

        if (!tienePermiso(path, method, rol)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("No tienes permisos para acceder a este recurso");
            return;
        }

        request.setAttribute("email", email);
        request.setAttribute("rol", rol);

        filterChain.doFilter(request, response);
    }

    private boolean tienePermiso(String path, String method, String rol) {
        if ("SUPER_ADMIN".equals(rol)) {
            return true;
        }

        boolean isGet = "GET".equalsIgnoreCase(method);

        if (path.startsWith("/ventas")) {
            if (isGet) return true;
            return "ADMIN_VENTAS".equals(rol);
        }
        if (path.startsWith("/inventario")) {
            if (isGet) return true;
            return "ADMIN_INVENTARIO".equals(rol);
        }
        if (path.startsWith("/finanzas")) {
            if (isGet) return true;
            return "ADMIN_FINANZAS".equals(rol);
        }
        if (path.startsWith("/clientes")) {
            if (isGet) return true;
            return "ADMIN_CLIENTES".equals(rol);
        }
        if (path.startsWith("/kpis")) {
            if (isGet) return true;
            return false;
        }
        if (path.startsWith("/reportes")) {
            return "EJECUTIVO".equals(rol) || "ANALISTA".equals(rol);
        }
        if (path.startsWith("/alertas")) {
            if (isGet) return true;
            return "EJECUTIVO".equals(rol) || "ANALISTA".equals(rol);
        }
        
        if (path.startsWith("/auth/admin")) {
            return "ADMIN_USUARIOS".equals(rol) || "SUPER_ADMIN".equals(rol);
        }

        return true;
    }
}
