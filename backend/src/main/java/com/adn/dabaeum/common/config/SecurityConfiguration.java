package com.adn.dabaeum.common.config;

import com.adn.dabaeum.common.security.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
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
        if (accountFilter != null) {
            http.addFilterAfter(accountFilter, DevBearerAuthenticationFilter.class);
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
