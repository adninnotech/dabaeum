package com.adn.dabaeum.publiccourse.config;

import com.adn.dabaeum.publiccourse.application.PublicCourseNormalizer;
import com.adn.dabaeum.publiccourse.application.PublicCourseProperties;
import com.adn.dabaeum.publiccourse.application.PublicCourseSourceKeys;
import com.adn.dabaeum.publiccourse.application.PublicCourseSyncService;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.infrastructure.openapi.PublicCourseApiResponseParser;
import com.adn.dabaeum.publiccourse.infrastructure.openapi.PublicCourseOpenApiClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 공공데이터 평생학습강좌 연동 (설계서 docs/specs/public-lifelong-courses.md).
 *
 * <p>수동 수집은 항상 쓸 수 있다. 자동 수집 스케줄러는 {@code dabaeum.public-courses.enabled=true} 이고
 * 배치가 켜져 있을 때만 만든다(통합 테스트·로컬 기본값은 꺼짐).
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(PublicCourseProperties.class)
public class PublicCourseConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    PublicCourseSource publicCourseSource(PublicCourseProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new PublicCourseOpenApiClient(restClient, new PublicCourseApiResponseParser(objectMapper),
            properties.baseUrl(), properties.serviceKey());
    }

    /** 수집은 드물고 길다. 공용 실행기를 점유하지 않도록 실행마다 전용 스레드를 쓴다(동시 실행은 DB 가 막는다). */
    @Bean
    PublicCourseSyncService publicCourseSyncService(
        PublicCourseSource source,
        PublicCourseRepository courses,
        PublicCourseSyncRunRepository runs,
        PublicCourseProperties properties,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("public-course-sync-");
        return new PublicCourseSyncService(source, courses, runs, properties, new PublicCourseNormalizer(),
            new PublicCourseSourceKeys(), objectMapper, new TransactionTemplate(transactionManager), executor, clock,
            UUID::randomUUID);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "dabaeum.public-courses", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "dabaeum.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableScheduling
    static class SchedulerConfiguration {

        @Bean
        PublicCourseSyncScheduler publicCourseSyncScheduler(PublicCourseSyncService service) {
            return new PublicCourseSyncScheduler(service);
        }
    }

    public static final class PublicCourseSyncScheduler {

        private static final Logger LOGGER = LoggerFactory.getLogger(PublicCourseSyncScheduler.class);

        private final PublicCourseSyncService service;

        PublicCourseSyncScheduler(PublicCourseSyncService service) {
            this.service = service;
        }

        @Scheduled(cron = "${dabaeum.public-courses.cron:0 0 3 * * *}", zone = "Asia/Seoul")
        public void runDaily() {
            try {
                service.startScheduled().ifPresent(runId -> LOGGER.info("PUBLIC_COURSE_SYNC_STARTED runId={}", runId));
            } catch (RuntimeException exception) {
                LOGGER.warn("PUBLIC_COURSE_SYNC_START_FAILED type={}", exception.getClass().getName(), exception);
            }
        }
    }
}
