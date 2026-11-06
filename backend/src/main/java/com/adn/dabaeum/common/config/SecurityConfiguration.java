package com.adn.dabaeum.common.config;

import com.adn.dabaeum.common.security.CurrentUserProvider;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    /**
     * 브라우저 교차 출처 호출 허용 목록. 기본은 비어 있어 CORS 를 열지 않는다. 테스트베드처럼
     * 파일로 연 페이지에서 백엔드를 부를 때만 {@code DABAEUM_CORS_ALLOWED_ORIGINS} 로 연다.
     * 자격증명(쿠키)은 쓰지 않고 Authorization 헤더만 쓰므로 {@code *} 도 허용한다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${dabaeum.security.cors.allowed-origins:}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource,
        ObjectProvider<DevBearerAuthenticationFilter> devBearerFilter,
        ObjectProvider<LocalAccountAuthenticationFilter> localAccountFilter,
        ObjectProvider<ProductionJwtAuthenticationFilter> productionJwtFilter,
        ObjectProvider<SecurityErrorWriter> securityErrorWriter
    ) throws Exception {
        SecurityErrorWriter errorWriter = securityErrorWriter.getIfAvailable();
        if (errorWriter != null) {
            http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(errorWriter)
                .accessDeniedHandler(errorWriter));
        }

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // 규칙표는 ApiAuthorizationRules 에 도메인별로 나눠 두었다.
                // 새 엔드포인트를 추가하면 ApiAuthorizationRuleCoverageTest 가 누락을 잡는다.
                ApiAuthorizationRules.apply(auth);
                auth.anyRequest().authenticated();
            });

        DevBearerAuthenticationFilter filter = devBearerFilter.getIfAvailable();
        if (filter != null) {
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }

        LocalAccountAuthenticationFilter accountFilter =
            localAccountFilter.getIfAvailable();
        if (accountFilter != null && filter != null) {
            http.addFilterAfter(accountFilter, DevBearerAuthenticationFilter.class);
        } else if (accountFilter != null) {
            // dev 에는 고정 Bearer 필터가 없다. 순서 기준을 Spring 기본 필터로 잡는다.
            http.addFilterBefore(accountFilter, UsernamePasswordAuthenticationFilter.class);
        }

        ProductionJwtAuthenticationFilter productionFilter =
            productionJwtFilter.getIfAvailable();
        if (productionFilter != null) {
            http.addFilterBefore(
                productionFilter,
                UsernamePasswordAuthenticationFilter.class
            );
        }

        return http.build();
    }
}
