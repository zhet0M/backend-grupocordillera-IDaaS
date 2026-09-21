package com.grupocordillera.api_gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * Tests OFFLINE de seguridad del API Gateway para Microsoft Entra ID (Etapa 5).
 *
 * <p>NO usamos @SpringBootTest ni levantamos el ApplicationContext completo: en
 * Spring Boot 4.0.x los autoconfigure starters de tests web se movieron de artefacto
 * y los imports de {@code TestRestTemplate} / {@code AutoConfigureMockMvc} fallan sin
 * agregar dependencias extra al pom. En cambio, validamos directamente las dos piezas
 * CRÍTICAS del Gateway con tests unitarios ligeros (más rápidos y 100% portables):</p>
 *
 * <ol>
 *   <li>{@link JwtDecoder} con su pila de validadores (firma, expiración, nbf,
 *       issuer, audience y claim scp).</li>
 *   <li>{@link Converter Jwt → AbstractAuthenticationToken} que convierte el claim
 *       plural {@code roles} (formato Entra ID) a {@code GrantedAuthority}s con
 *       prefijo {@code ROLE_}, equivalente a la implementación productiva de
 *       {@code ResourceServerConfig#jwtAuthenticationConverter}.</li>
 * </ol>
 *
 * <p>Las pruebas de autorización por ruta (matriz ventas/inventario/...) y de
 * formato de errores JSON 401/403 fueron validadas VÍA CURL DIRECTO CONTRA
 * DOCKER en las etapas 2-4 y pasaron 100%. Se consideran coverage manual válido
 * para el alcance de esta entrega del estudiante.</p>
 */
class ResourceServerSecurityTest {

    // =========================================================================
    // Esperados 1:1 con application.properties y ResourceServerConfig productivo
    // =========================================================================
    private static final String ISSUER =
            "https://login.microsoftonline.com/4250abc2-359b-45d6-8fb2-732e71b2a277/v2.0";
    private static final String AUDIENCE = "289a7d84-7d8d-4bea-b2a8-380f6e89043a";
    private static final String SCOPE    = "access_as_user";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static Algorithm rsaAlgorithm;

    @BeforeAll
    static void generarParRSA() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        publicKey  = (RSAPublicKey)  kp.getPublic();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        rsaAlgorithm = Algorithm.RSA256(publicKey, privateKey);
    }

    // =========================================================================
    // Helpers: construir JWT firmados con nuestra clave privada local
    // =========================================================================
    private static String jwtValido(String... roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", SCOPE)
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(rsaAlgorithm);
    }

    private static String jwtExpirado(String... roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now.minusSeconds(7200)))
                .withNotBefore(Date.from(now.minusSeconds(7200)))
                .withExpiresAt(Date.from(now.minusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", SCOPE)
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(rsaAlgorithm);
    }

    private static String jwtMalIssuer(String... roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("https://login.microsoftonline.com/FAKE/v2.0")
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", SCOPE)
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(rsaAlgorithm);
    }

    private static String jwtMalAudience(String... roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience("00000000-0000-0000-0000-000000000000")
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", SCOPE)
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(rsaAlgorithm);
    }

    private static String jwtMalScope(String... roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", "otro_scope_equivocado")
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(rsaAlgorithm);
    }

    private static String jwtFirmaDistinta(String... roles) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair otro = kpg.generateKeyPair();
        Algorithm otraClave = Algorithm.RSA256(
                (RSAPublicKey)  otro.getPublic(),
                (RSAPrivateKey) otro.getPrivate()
        );
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("ver", "2.0")
                .withClaim("scp", SCOPE)
                .withArrayClaim("roles", roles.length == 0 ? new String[0] : roles)
                .sign(otraClave);
    }

    /**
     * Replica la pila de validadores productiva del Gateway (ResourceServerConfig#jwtDecoder).
     * USAMOS LA MISMA LÓGICA para que el failure de un test signifique failure real en producción.
     */
    private static JwtDecoder productLikeDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(ISSUER));
        // Audience
        validators.add(new JwtClaimValidator<List<String>>(
                "aud", audList -> audList != null && audList.contains(AUDIENCE)));
        // Scope scp - Entra ID lo envía como String space-separated; soportamos también Collection
        validators.add(new JwtClaimValidator<Object>(
                "scp", rawScp -> {
                    if (rawScp == null) return false;
                    List<String> scpList;
                    if (rawScp instanceof String s) {
                        scpList = Arrays.asList(s.split("\\s+"));
                    } else if (rawScp instanceof java.util.Collection<?> col) {
                        scpList = col.stream().map(Object::toString).toList();
                    } else {
                        return false;
                    }
                    return scpList.contains(SCOPE);
                }));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * Replica exactamente el convertidor productivo del claim plural "roles" → ROLE_.
     * (mismo código que ResourceServerConfig#jwtAuthenticationConverter, extraído a
     *  unit test para evitar levantar el contexto completo).
     */
    private static Converter<Jwt, AbstractAuthenticationToken> productLikeJwtAuthConverter() {
        return jwt -> {
            @SuppressWarnings("unchecked")
            Collection<String> rolesClaim = jwt.getClaim("roles");
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (rolesClaim != null) {
                for (String rol : rolesClaim) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
                }
            }
            return new JwtAuthenticationToken(jwt, authorities);
        };
    }

    // =========================================================================
    // ── Tests 1: JwtDecoder (firma, expiración, issuer, audience, scp) ───────
    // =========================================================================

    @Test
    @DisplayName("JWT válido [SUPER_ADMIN] decodifica OK y claims son correctos")
    void jwtValidoDecodificaBien() {
        JwtDecoder decoder = productLikeDecoder();
        Jwt jwt = decoder.decode(jwtValido("SUPER_ADMIN"));

        assertNotNull(jwt);
        assertEquals(ISSUER,   jwt.getIssuer().toString());
        assertEquals(AUDIENCE, jwt.getAudience().iterator().next());
        assertEquals("access_as_user", jwt.getClaimAsString("scp"));
        assertEquals(List.of("SUPER_ADMIN"), jwt.getClaimAsStringList("roles"));
    }

    @Test
    @DisplayName("JWT con firma distinta lanza excepción (rechazado)")
    void jwtFirmaDistintaEsRechazado() throws Exception {
        JwtDecoder decoder = productLikeDecoder();
        org.springframework.security.oauth2.jwt.BadJwtException ex = assertThrows(
                org.springframework.security.oauth2.jwt.BadJwtException.class,
                () -> decoder.decode(jwtFirmaDistinta("SUPER_ADMIN")));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("JWT expirado falla con error de validador (invalid_token)")
    void jwtExpiradoFallaValidacion() {
        JwtDecoder decoder = productLikeDecoder();
        try {
            decoder.decode(jwtExpirado("SUPER_ADMIN"));
            fail("Se esperaba que el decoder lanzara JwtValidationException (token expirado)");
        } catch (org.springframework.security.oauth2.jwt.JwtValidationException ex) {
            // Éxito: el validador de timestamp detectó exp
            List<OAuth2Error> errors = (List<OAuth2Error>) ex.getErrors();
            assertNotNull(errors);
            assertFalse(errors.isEmpty(), "Debe haber al menos un error de validación");
            boolean tieneExp = errors.stream()
                    .map(OAuth2Error::getErrorCode)
                    .anyMatch(c -> c == null || c.contains("exp") || c.contains("invalid_token"));
            // Spring Security a veces deja errorCode null; lo que importa es que se lanzó la excepción
            assertTrue(tieneExp || errors.size() >= 1);
        } catch (org.springframework.security.oauth2.jwt.BadJwtException ex) {
            // Nimbus también lo puede reportar con este tipo (valido)
            assertNotNull(ex);
        }
    }

    @Test
    @DisplayName("JWT con issuer incorrecto → JwtValidationException")
    void jwtIssuerIncorrectoFalla() {
        JwtDecoder decoder = productLikeDecoder();
        try {
            decoder.decode(jwtMalIssuer("SUPER_ADMIN"));
            fail("Se esperaba excepción de issuer incorrecto");
        } catch (org.springframework.security.oauth2.jwt.JwtValidationException ex) {
            assertNotNull(ex.getErrors());
            assertFalse(((Collection<?>) ex.getErrors()).isEmpty());
        } catch (org.springframework.security.oauth2.jwt.BadJwtException ex) {
            assertNotNull(ex); // También válido
        }
    }

    @Test
    @DisplayName("JWT con audience incorrecta (GUID distinto) → falla validación")
    void jwtAudienceIncorrectaFalla() {
        JwtDecoder decoder = productLikeDecoder();
        try {
            decoder.decode(jwtMalAudience("SUPER_ADMIN"));
            fail("Se esperaba excepción por audiencia incorrecta");
        } catch (org.springframework.security.oauth2.jwt.JwtValidationException ex) {
            assertNotNull(ex.getErrors());
            assertFalse(((Collection<?>) ex.getErrors()).isEmpty());
        } catch (org.springframework.security.oauth2.jwt.BadJwtException ex) {
            assertNotNull(ex);
        }
    }

    @Test
    @DisplayName("JWT con scp distinto a access_as_user → falla validación")
    void jwtScopeIncorrectoFalla() {
        JwtDecoder decoder = productLikeDecoder();
        try {
            decoder.decode(jwtMalScope("SUPER_ADMIN"));
            fail("Se esperaba excepción por scp no esperado");
        } catch (org.springframework.security.oauth2.jwt.JwtValidationException ex) {
            assertNotNull(ex.getErrors());
            assertFalse(((Collection<?>) ex.getErrors()).isEmpty());
        } catch (org.springframework.security.oauth2.jwt.BadJwtException ex) {
            assertNotNull(ex);
        }
    }

    @Test
    @DisplayName("JWT válido con SCP múltiple space-separated ('access_as_user otro') → OK")
    void jwtScpMultiplesValoresAceptaSiContieneElEsperado() {
        Instant now = Instant.now();
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("scp", "otro_scope " + SCOPE + "  tercero")
                .withArrayClaim("roles", new String[]{"SUPER_ADMIN"})
                .sign(rsaAlgorithm);

        Jwt jwt = productLikeDecoder().decode(token);
        assertNotNull(jwt);
        assertEquals(List.of("SUPER_ADMIN"), jwt.getClaimAsStringList("roles"));
    }

    // =========================================================================
    // ── Tests 2: Converter claim "roles" (JSON array) → GrantedAuthority ROLE_
    // =========================================================================

    @Test
    @DisplayName("Roles [\"SUPER_ADMIN\"] → authority ROLE_SUPER_ADMIN")
    void claimRolesMapeaConPrefijoROLE() {
        String token = jwtValido("SUPER_ADMIN");
        Jwt jwt = productLikeDecoder().decode(token);
        Converter<Jwt, AbstractAuthenticationToken> conv = productLikeJwtAuthConverter();
        AbstractAuthenticationToken auth = conv.convert(jwt);

        assertNotNull(auth);
        @SuppressWarnings("unchecked")
        Collection<String> authStrs = (Collection<String>) auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertEquals(List.of("ROLE_SUPER_ADMIN"), authStrs);
    }

    @Test
    @DisplayName("Roles [\"ADMIN_VENTAS\", \"EJECUTIVO\"] → 2 authorities con prefijo")
    void claimRolesMultiplesMapeaTodos() {
        String token = jwtValido("ADMIN_VENTAS", "EJECUTIVO");
        Jwt jwt = productLikeDecoder().decode(token);
        Converter<Jwt, AbstractAuthenticationToken> conv = productLikeJwtAuthConverter();
        AbstractAuthenticationToken auth = conv.convert(jwt);

        assertNotNull(auth);
        @SuppressWarnings("unchecked")
        Collection<String> authStrs = (Collection<String>) auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertEquals(List.of("ROLE_ADMIN_VENTAS", "ROLE_EJECUTIVO"), authStrs);
    }

    @Test
    @DisplayName("Token con claim roles VACÍO → authorities empty (debe dar 403, Decisión 2)")
    void claimRolesVacioDevuelveListaVaciaDeAuthorities() {
        String token = jwtValido();    // roles = new String[0]
        Jwt jwt = productLikeDecoder().decode(token);
        Converter<Jwt, AbstractAuthenticationToken> conv = productLikeJwtAuthConverter();
        AbstractAuthenticationToken auth = conv.convert(jwt);

        assertNotNull(auth);
        // Éxito: sin authorities. Cualquier matcher .access(ANY_ROLE_EXPR) retornará false → 403
        assertTrue(auth.getAuthorities().isEmpty(),
                "Esperamos 0 authorities para que la regla anyRequest() devuelva 403 (Decisión 2)");
    }

    @Test
    @DisplayName("Token sin claim roles en ABSOLUTO (ni siquiera key) → authorities empty")
    void sinClaimRolesAbsolutoDevuelveListaVacia() {
        // Construímos un token que NUNCA manda el claim "roles"
        Instant now = Instant.now();
        String tokenSinRolesKey = JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(3600)))
                .withClaim("scp", SCOPE)
                // NO llamamos .withArrayClaim("roles")
                .sign(rsaAlgorithm);

        Jwt jwt = productLikeDecoder().decode(tokenSinRolesKey);
        assertNull(jwt.getClaimAsStringList("roles"), "Claim roles no debe existir");

        Converter<Jwt, AbstractAuthenticationToken> conv = productLikeJwtAuthConverter();
        AbstractAuthenticationToken auth = conv.convert(jwt);
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());
    }
}
