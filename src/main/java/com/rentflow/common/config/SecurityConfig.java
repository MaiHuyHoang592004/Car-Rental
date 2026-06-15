package com.rentflow.common.config;

import com.rentflow.common.security.JwtAuthenticationEntryPoint;
import com.rentflow.common.security.JwtAuthenticationFilter;
import com.rentflow.common.security.JsonAccessDeniedHandler;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final String allowedOrigins;
    private final Environment environment;
    private final boolean swaggerUiEnabled;
    private final boolean apiDocsEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler,
                          @Value("${rentflow.cors.allowed-origins:http://localhost:3000}") String allowedOrigins,
                          @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled,
                          @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
                          Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.allowedOrigins = allowedOrigins;
        this.swaggerUiEnabled = swaggerUiEnabled;
        this.apiDocsEnabled = apiDocsEnabled;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                    auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout-all").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN");

                    if (isSwaggerPublic()) {
                        auth.requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/swagger-ui.html").permitAll()
                                .requestMatchers("/api-docs/**").permitAll()
                                .requestMatchers("/v3/api-docs/**").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui/**").denyAll()
                                .requestMatchers("/swagger-ui.html").denyAll()
                                .requestMatchers("/api-docs/**").denyAll()
                                .requestMatchers("/v3/api-docs/**").denyAll();
                    }

                    auth
                        // Public listing endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}/availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/protection-plans").permitAll()
                        // Payment bank catalog is used inside authenticated checkout flow in Slice 6A
                        .requestMatchers(HttpMethod.GET, "/api/v1/payment-banks").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/{id}/payments/authorize").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}/payments").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/capture").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/void").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/refund").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/{id}/reconciliation").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/me").authenticated()
                        // Booking endpoints require authentication; service layer enforces roles and ownership
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/{id}/cancel").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}/cancel-preview").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/{id}").authenticated()
                        // Admin endpoints require ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/host/**").hasRole("HOST")
                        // All other endpoints require authentication
                        .anyRequest().authenticated();
            });

        // Add JWT filter BEFORE AuthorizationFilter so token validation happens before auth check
        http.addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = explicitAllowedOrigins();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Correlation-Id",
                "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private List<String> explicitAllowedOrigins() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("rentflow.cors.allowed-origins must include at least one explicit origin");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "rentflow.cors.allowed-origins must not contain '*' when CORS credentials are enabled");
        }
        return origins;
    }

    private boolean isSwaggerPublic() {
        boolean docsEnabled = swaggerUiEnabled || apiDocsEnabled;
        boolean localOrTest = environment.matchesProfiles("local", "test");
        return docsEnabled && localOrTest;
    }
}
