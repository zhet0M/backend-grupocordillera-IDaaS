package com.autenticacion.authentication.config;

import com.autenticacion.authentication.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * @deprecated (Etapa 4 - Migración a Microsoft Entra ID).
 * <p>Este filtro JWT HMAC propietario ha sido reemplazado COMPLETAMENTE por el
 * API Gateway (BFF) configurado como OAuth2 Resource Server JWT que valida
 * tokens de Entra ID via JWKS + Nimbus.
 *
 * <p>Se eliminó @Component para que Spring NO lo registre automaticamente y en
 * {@link SecurityConfig} se retiró su addFilterBefore(). La clase se conserva
 * únicamente como referencia histórica del flujo anterior (NO se borra código).
 */
@Deprecated(since = "Etapa 4 Entra ID")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        final String userRol;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        
        try {
            userEmail = jwtService.extraerUsername(jwt);
            userRol = jwtService.extraerRol(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.validarToken(jwt)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(userRol))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token inválido
        }

        filterChain.doFilter(request, response);
    }
}
