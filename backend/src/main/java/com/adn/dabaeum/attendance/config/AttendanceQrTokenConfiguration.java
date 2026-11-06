package com.adn.dabaeum.attendance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

/**
 * DadaeguAttendProperties 는 여기서 프로필 없이 등록한다. 다대구 게이트웨이·세션 서비스는
 * local/dev 전용이지만, QR 발급 응답의 attendUrl 을 만드는 AttendanceApiMapper 는
 * 모든 프로필에 있기 때문이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    AttendanceQrTokenProperties.class,
    DadaeguAttendProperties.class
})
public class AttendanceQrTokenConfiguration {

    /**
     * 다대구 출석 세션의 보험 확인("이 세션, 45초·90초 뒤 한 번")을 거는 스케줄러.
     * {@code @EnableScheduling} 은 Fabric 워커가 켜졌을 때만 활성화돼 스프링 기본 빈이 없다.
     * 작업마다 가상 스레드라 다대구 응답이 느려도 다른 세션의 보험이 뒤에서 밀리지 않는다.
     */
    @Bean
    @Profile({"local", "dev"})
    TaskScheduler dadaeguAttendTaskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("dadaegu-attend-");
        return scheduler;
    }
}
