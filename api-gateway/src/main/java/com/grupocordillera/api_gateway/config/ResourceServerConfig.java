package com.grupocordillera.api_gateway.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Configuración central del API Gateway como BFF (OAuth2 Resource Server JWT)
 * para Microsoft Entra ID.
 *
 * <p>Responsabilidades en esta Etapa 1:
 * <ol>
 *   <li>Validar el access token de Entra ID contra el JWKS público de Microsoft.</li>
 *   <li>Validar obligatoriamente: firma, expiración (exp/nbf), issuer y audience.</li>
 *   <li>Convertir el claim {@code roles} (JSON array del access token) en authorities
 *       con el prefijo {@code ROLE_} (requerido por Spring Security para hasRole).</li>
 *   <li>Exponer rutas públicas base (health, swagger si security.swagger-public=true,
 *       OPTIONS, /auth/login y /auth/registro legacy por ahora). Todo lo demás requiere
 *       autenticación. La autorización FINA por rol se implementa en Etapa 2.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    // =========================================================================
    // Roles (Spring Security exige prefijo ROLE_ para hasRole(...)).
    // Coinciden 1:1 con los roles que Microsoft Entra emite en el claim "roles".
    // =========================================================================
    private static final String ROLE_SUPER_ADMIN      = "SUPER_ADMIN";
    private static final String ROLE_ADMIN_USUARIOS   = "ADMIN_USUARIOS";
    private static final String ROLE_ADMIN_VENTAS     = "ADMIN_VENTAS";
    private static final String ROLE_ADMIN_INVENTARIO = "ADMIN_INVENTARIO";
    private static final String ROLE_ADMIN_FINANZAS   = "ADMIN_FINANZAS";
    private static final String ROLE_ADMIN_CLIENTES   = "ADMIN_CLIENTES";
    private static final String ROLE_EJECUTIVO        = "EJECUTIVO";
    private static final String ROLE_ANALISTA         = "ANALISTA";

    /**
     * Decisión 2: "Autenticado" == token válido + al menos uno de los 8 roles.
     * Un token válido sin roles recibe 403, no 200.
     * Usamos esta expresión en TODO lugar donde antes poníamos .authenticated().
     */
    private static final String ANY_ROLE_EXPR =
            "hasRole('" + ROLE_SUPER_ADMIN      + "') or hasRole('" + ROLE_ADMIN_USUARIOS   + "') or " +
            "hasRole('" + ROLE_ADMIN_VENTAS     + "') or hasRole('" + ROLE_ADMIN_INVENTARIO + "') or " +
            "hasRole('" + ROLE_ADMIN_FINANZAS   + "') or hasRole('" + ROLE_ADMIN_CLIENTES   + "') or " +
            "hasRole('" + ROLE_EJECUTIVO        + "') or hasRole('" + ROLE_ANALISTA         + "')";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${azure.issuer}")
    private String azureIssuer;

    @Value("${azure.jwk-set-uri}")
    private String azureJwkSetUri;

    @Value("${azure.audience}")
    private String azureAudience;

    @Value("${azure.api-scope:access_as_user}")
    private String azureApiScope;

    @Value("${security.swagger-public:true}")
    private boolean swaggerPublic;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // Rutas públicas base.
        final List<String> publicPaths = new ArrayList<>();
        publicPaths.add("/actuator/health");
        if (Boolean.TRUE.equals(swaggerPublic)) {
            publicPaths.add("/swagger-ui.html");
            publicPaths.add("/swagger-ui/**");
            publicPaths.add("/v3/api-docs");
            publicPaths.add("/v3/api-docs/**");
            publicPaths.add("/webjars/**");
        }
        // NOTA Etapa 4: /auth/login y /auth/registro NO son públicos.
        // Quedaron deshabilitados en el MS authentication (Controller devuelve 410 Gone).
        // Se marcan PERMIT_ALL ABAJO (antes de la matriz) para que la petición PUEDA
        // pasar sin token y llegar hasta authentication, mostrando al usuario el mensaje
        // HTTP 410 explicito en lugar de un 401/403 genérico del Gateway.
        final String[] authLegacyPaths = new String[] { "/auth/login", "/auth/registro" };

        http
            // CORS se gestiona EXCLUSIVAMENTE en GatewayConfig (filtro orden 1).
            // Desactivamos el CorsFilter integrado de Spring Security para no
            // registrar dos procesadores CORS (era causa probable de cuerpo vacío en 401).
            .cors(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ===== OAuth2 Resource Server + errores JSON uniformes ====
            .oauth2ResourceServer(rs -> rs
                    .authenticationEntryPoint(this::authenticationEntryPoint)
                    .accessDeniedHandler(this::accessDeniedHandler)
                    .jwt(jwt -> {
                        jwt.decoder(jwtDecoder());
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter());
                    })
            )

            // ===== MATRIZ DE AUTORIZACIÓN confirmada en Etapa 0 =====
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()

                // Flujos legacy DESHABILITADOS (Etapa 4): permitAll() SOLO para que
                // lleguen al Controller de authentication y este devuelva 410 Gone
                // con mensaje explicito al usuario. NO son rutas funcionales.
                .requestMatchers(authLegacyPaths).permitAll()

                // /auth/admin/**: SUPER_ADMIN o ADMIN_USUARIOS
                .requestMatchers("/auth/admin/**")
                        .access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_USUARIOS + "')"))

                // Ventas: GET requiere al menos un rol; escritura solo SUPER_ADMIN / ADMIN_VENTAS
                .requestMatchers(HttpMethod.GET, "/ventas/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/ventas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_VENTAS + "')"))
                .requestMatchers(HttpMethod.PUT,    "/ventas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_VENTAS + "')"))
                .requestMatchers(HttpMethod.DELETE, "/ventas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_VENTAS + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/ventas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_VENTAS + "')"))

                // Inventario: GET requiere al menos un rol; escritura solo SUPER_ADMIN / ADMIN_INVENTARIO
                .requestMatchers(HttpMethod.GET, "/inventario/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/inventario/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_INVENTARIO + "')"))
                .requestMatchers(HttpMethod.PUT,    "/inventario/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_INVENTARIO + "')"))
                .requestMatchers(HttpMethod.DELETE, "/inventario/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_INVENTARIO + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/inventario/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_INVENTARIO + "')"))

                // Finanzas: GET requiere al menos un rol; escritura solo SUPER_ADMIN / ADMIN_FINANZAS
                .requestMatchers(HttpMethod.GET, "/finanzas/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/finanzas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_FINANZAS + "')"))
                .requestMatchers(HttpMethod.PUT,    "/finanzas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_FINANZAS + "')"))
                .requestMatchers(HttpMethod.DELETE, "/finanzas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_FINANZAS + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/finanzas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_FINANZAS + "')"))

                // Clientes: GET requiere al menos un rol; escritura solo SUPER_ADMIN / ADMIN_CLIENTES
                .requestMatchers(HttpMethod.GET, "/clientes/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/clientes/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_CLIENTES + "')"))
                .requestMatchers(HttpMethod.PUT,    "/clientes/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_CLIENTES + "')"))
                .requestMatchers(HttpMethod.DELETE, "/clientes/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_CLIENTES + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/clientes/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_ADMIN_CLIENTES + "')"))

                // KPIs: GET requiere al menos un rol; escritura solo SUPER_ADMIN
                .requestMatchers(HttpMethod.GET, "/kpis/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/kpis/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "')"))
                .requestMatchers(HttpMethod.PUT,    "/kpis/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "')"))
                .requestMatchers(HttpMethod.DELETE, "/kpis/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/kpis/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "')"))

                // Reportes: cualquier método -> SUPER_ADMIN, EJECUTIVO, ANALISTA
                .requestMatchers("/reportes/**")
                        .access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_EJECUTIVO + "') or hasRole('" + ROLE_ANALISTA + "')"))

                // Alertas: GET requiere al menos un rol; escritura SUPER_ADMIN, EJECUTIVO, ANALISTA
                .requestMatchers(HttpMethod.GET, "/alertas/**").access(expr(ANY_ROLE_EXPR))
                .requestMatchers(HttpMethod.POST,   "/alertas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_EJECUTIVO + "') or hasRole('" + ROLE_ANALISTA + "')"))
                .requestMatchers(HttpMethod.PUT,    "/alertas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_EJECUTIVO + "') or hasRole('" + ROLE_ANALISTA + "')"))
                .requestMatchers(HttpMethod.DELETE, "/alertas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_EJECUTIVO + "') or hasRole('" + ROLE_ANALISTA + "')"))
                .requestMatchers(HttpMethod.PATCH,  "/alertas/**").access(expr("hasRole('" + ROLE_SUPER_ADMIN + "') or hasRole('" + ROLE_EJECUTIVO + "') or hasRole('" + ROLE_ANALISTA + "')"))

                // Resto: requiere al menos un rol (decisión 2: token sin roles -> 403).
                .anyRequest().access(expr(ANY_ROLE_EXPR))
            );

        return http.build();
    }

    /** Helper: crea un WebExpressionAuthorizationManager con la expresión dada. */
    private static org.springframework.security.authorization.AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext> expr(
            String spelExpression
    ) {
        return new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager(spelExpression);
    }

    /**
     * 401 Unauthorized. JSON uniforme + header WWW-Authenticate: Bearer.
     * Mensajes NO revelan claims especificos ni datos internos.
     */
    private void authenticationEntryPoint(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException
    ) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String errorCode = "invalid_token";
        String message = "Token inválido, expirado o con claims inválidos";

        if (authException instanceof org.springframework.security.authentication.AuthenticationCredentialsNotFoundException) {
            errorCode = "invalid_request";
            message = "Token no proporcionado";
        } else if (authException instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth) {
            if (oauth.getError() != null) {
                if (oauth.getError().getErrorCode() != null) {
                    errorCode = oauth.getError().getErrorCode();
                }
                message = normalizeOAuth2Error(oauth.getError());
            }
        }

        response.setHeader(org.springframework.http.HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"" + errorCode + "\", error_description=\"" + message.replace('"', '\'') + "\"");

        response.getWriter().write(objectMapper.writeValueAsString(
                errorBody(HttpStatus.UNAUTHORIZED, "Unauthorized", message, request.getRequestURI())
        ));
    }

    /** 403 Forbidden. JSON uniforme, sin detalles internos. */
    private void accessDeniedHandler(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException
    ) throws java.io.IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                errorBody(HttpStatus.FORBIDDEN, "Forbidden",
                        "No tienes los roles necesarios para acceder a este recurso.",
                        request.getRequestURI())
        ));
    }

    /** Mensaje amigable al usuario; NO expone aud, iss, claims. */
    private String normalizeOAuth2Error(OAuth2Error err) {
        if (err == null || err.getErrorCode() == null) {
            return "Token inválido o expirado";
        }
        return switch (err.getErrorCode()) {
            case "invalid_token" -> "Token inválido, expirado o con claims inválidos";
            case "invalid_request" -> "Token no proporcionado o formato incorrecto";
            case "insufficient_scope" -> "El token no contiene el scope esperado '" + azureApiScope + "'";
            default -> "Token inválido o expirado";
        };
    }

    /** Body de error uniforme: timestamp, status, error, message, path. */
    private Map<String, Object> errorBody(
            HttpStatus status,
            String error,
            String message,
            String path
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    /**
     * JwtDecoder custom: usamos jwk-set-uri (NO issuer-uri), por lo que NO
     * se hace ninguna llamada a Microsoft al arrancar.
     *
     * <p>Validadores aplicados (en orden):
     * <ul>
     *   <li>{@link JwtTimestampValidator}: exp (expirado) / nbf (no antes de).</li>
     *   <li>{@link JwtIssuerValidator}: issuer EXACTO igual a azure.issuer.</li>
     *   <li>{@link JwtClaimValidator} para {@code aud}: debe contener exactamente
     *       {@code azure.audience} (valor esperado: Client ID de la API, GUID).
     *       <b>NO se relaja esta validación</b> aunque llegara con prefijo {@code api://};
     *       si eso ocurre en tus pruebas se reporta como error y se ajusta el
     *       registro de Azure, NO el validador.</li>
     * </ul>
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Timeouts más tolerantes para entornos Docker / VPNs / slow connections.
        // El default ~5s hace Read timed out al descargar el JWKS de Microsoft.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30_000);
        requestFactory.setReadTimeout(30_000);
        RestOperations restOperations = new RestTemplate(requestFactory);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(azureJwkSetUri)
                .restOperations(restOperations)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(azureIssuer));
        // Audience OBLIGATORIA y EXACTA (GUID, sin prefijo api://).
        validators.add(new JwtClaimValidator<List<String>>("aud", audList ->
                audList != null && audList.contains(azureAudience)));
        // Scope OBLIGATORIO: claim "scp" viene como String space-separated (v2 Entra ID)
        // OPCIONALMENTE como List<String>. Aceptamos ambos formatos para ser robustos.
        validators.add(new JwtClaimValidator<Object>("scp", rawScp -> {
            if (rawScp == null) return false;
            List<String> scpList;
            if (rawScp instanceof String s) {
                scpList = Arrays.asList(s.split("\\s+"));
            } else if (rawScp instanceof Collection<?> col) {
                scpList = col.stream().map(Object::toString).toList();
            } else {
                return false;
            }
            return scpList.contains(azureApiScope);
        }));

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(validators);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Extrae:
     * - claim "roles" (JSON array) -> authorities ROLE_* (Spring hasRole)).
     * - claim "scp" (space-separated or JSON array) -> authorities SCOPE_*.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter = jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // ROLE_* desde claim "roles"
            Object rawRoles = jwt.getClaim("roles");
            if (rawRoles instanceof Collection<?> collection) {
                collection.stream()
                        .map(Object::toString)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .forEach(authorities::add);
            }

            // SCOPE_* desde claim "scp"
            List<String> scpList = jwt.getClaimAsStringList("scp");
            if (scpList != null && !scpList.isEmpty()) {
                scpList.stream()
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .forEach(authorities::add);
            }

            return authorities;
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return converter;
    }
}
