package com.adn.dabaeum.accesslog.web;

import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @WebMvcTest 도 이 설정을 올린다. 저장소·Clock 을 ObjectProvider 로 받아, 빈이 없는 컨트롤러 테스트에서는
 * 인터셉터가 아무것도 하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class AccessLogWebConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<AdminAccessLogRepository> repository;
    private final ObjectProvider<Clock> clock;

    public AccessLogWebConfiguration(
        ObjectProvider<AdminAccessLogRepository> repository,
        ObjectProvider<Clock> clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAccessLogInterceptor(repository, clock.getIfAvailable(Clock::systemUTC)))
            .addPathPatterns("/api/v1/**");
    }
}
