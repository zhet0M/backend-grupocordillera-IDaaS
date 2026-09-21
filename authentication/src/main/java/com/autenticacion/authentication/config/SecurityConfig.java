package com.autenticacion.authentication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad del microservicio AUTHENTICATION (Etapa 4 Entra ID).
 *
 * <p><b>Cambios importantes vs Etapa anterior:</b>
 * <ol>
 *   <li>El filtro JWT propio de este servicio <b>ya no está registrado</b>.
 *       Anteriormente validaba un JWT HMAC propietario; hoy la validación de
 *       tokens de Microsoft Entra ID la hace EXCLUSIVAMENTE el API Gateway
 *       (OAuth2 Resource Server) antes de enrutar hacia acá.</li>
 *   <li>{@code /auth/login} y {@code /auth/registro} han sido <b>deshabilitados</b>
 *       (ver {@code AuthController}). Quitar su {@code permitAll()} aquí hace que
 *       Spring Security los rechace con 403/401 si alguien los llama por accidente
 *       desde dentro de la red Docker, reforzando la deshabilitación.</li>
 *   <li>{@code /auth/admin/**} sigue protegido, pero en la práctica es el GATEWAY
 *       quien ya chequeó SUPER_ADMIN / ADMIN_USUARIOS antes de enrutar. Dejamos
 *       la regla como segunda capa de defensa (defense-in-depth).</li>
 *   <li>Swagger y OPTIONS siguen públicos para que esta instancia sea
 *       inspeccionable desde redes internas.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // /auth/login y /auth/registro DESHABILITADOS (no permitAll)
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/webjars/**").permitAll()
                // Segunda capa: roles /auth/admin/** (ya validados en Gateway)
                .requestMatchers("/auth/admin/**").hasAnyAuthority(
                        "ROLE_SUPER_ADMIN",
                        "SUPER_ADMIN",
                        "ROLE_ADMIN_USUARIOS",
                        "ADMIN_USUARIOS"
                )
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}

