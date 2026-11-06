package com.adn.dabaeum.view.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import com.adn.dabaeum.view.domain.CourseViewRepository;
import com.adn.dabaeum.view.domain.ViewSaltRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 조회 로그·salt 1년 보존 (설계서 §14). 인기 집계(tb_course_popularity)는 계속 남는다. */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class ViewRetentionTasks {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static LocalDate oneYearBefore(Instant now) {
        return now.atZone(SEOUL).toLocalDate().minusYears(1);
    }

    @Bean
    RetentionTask courseViewRetentionTask(CourseViewRepository views) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "course-views";
            }

            @Override
            public long lockKey() {
                return AdvisoryLockKeys.COURSE_VIEW_CLEANUP;
            }

            @Override
            public int purge(Instant now) {
                return views.deleteViewsBefore(oneYearBefore(now));
            }
        };
    }

    @Bean
    RetentionTask viewSaltRetentionTask(ViewSaltRepository salts) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "view-salts";
            }

            @Override
            public long lockKey() {
                return AdvisoryLockKeys.VIEW_SALT_CLEANUP;
            }

            @Override
            public int purge(Instant now) {
                return salts.deleteSaltsBefore(oneYearBefore(now));
            }
        };
    }
}
