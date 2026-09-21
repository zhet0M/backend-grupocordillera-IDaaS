package com.grupocordillera.api_gateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Sobrescribe el bean {@link JwtDecoder} productivo durante TODOS los tests OFFLINE.
 *
 * <p>El decoder productivo ({@link ResourceServerConfig#jwtDecoder()}) está configurado
 * con el JWKS público de Microsoft Entra ID. Como los tests corren sin conexión, usar
 * ese bean haría que el PRIMER token que intentemos decodificar falle con
 * {@code Read timed out} al intentar descargar las claves públicas de Microsoft.</p>
 *
 * <p>Este decoder fallback lanza {@link BadJwtException} por defecto para cualquier
 * token (indicando que el test no debía llegar al decoder productivo). En las clases
 * de test que necesiten decodificar tokens firmados con una clave RSA local, se
 * provee su propia implementación {@code @Primary} de JwtDecoder dentro del propio
 * test mediante clases internas {@code @TestConfiguration}.</p>
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder offlineJwtDecoder() {
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) {
                throw new BadJwtException(
                        "JwtDecoder OFFLINE de test: token no procesable. " +
                        "Este test debe proveer su propio @TestConfiguration con " +
                        "NimbusJwtDecoder + publicKey local para procesar el token."
                );
            }
        };
    }
}
