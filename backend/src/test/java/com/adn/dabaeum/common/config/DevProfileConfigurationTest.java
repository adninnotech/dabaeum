package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.application.RandomAttendanceIdGenerator;
import com.adn.dabaeum.attendance.infrastructure.crypto.EphemeralAttendanceQrKeyProvider;
import com.adn.dabaeum.attendance.infrastructure.mybatis.AttendanceAdjustmentMyBatisRepository;
import com.adn.dabaeum.attendance.infrastructure.mybatis.AttendanceMyBatisRepository;
import com.adn.dabaeum.attendance.infrastructure.mybatis.AttendanceQrTokenMyBatisRepository;
import com.adn.dabaeum.common.api.OpenApiContractController;
import com.adn.dabaeum.common.infrastructure.database.LocalDataSourceConfiguration;
import com.adn.dabaeum.common.infrastructure.ssh.SshTunnelConfiguration;
import com.adn.dabaeum.completion.application.RandomCompletionIdGenerator;
import com.adn.dabaeum.completion.infrastructure.mybatis.CompletionMyBatisRepository;
import com.adn.dabaeum.completion.infrastructure.mybatis.CompletionOutboxMyBatisRepository;
import com.adn.dabaeum.course.application.RandomCourseSessionIdGenerator;
import com.adn.dabaeum.course.infrastructure.mybatis.CourseInstructorMyBatisRepository;
import com.adn.dabaeum.course.infrastructure.mybatis.CourseMyBatisRepository;
import com.adn.dabaeum.course.infrastructure.mybatis.CourseSessionMyBatisRepository;
import com.adn.dabaeum.enrollment.application.RandomEnrollmentIdGenerator;
import com.adn.dabaeum.enrollment.infrastructure.mybatis.EnrollmentMyBatisRepository;
import com.adn.dabaeum.identity.infrastructure.mybatis.UserIdentityMyBatisRepository;
import com.adn.dabaeum.institution.infrastructure.mybatis.InstitutionMyBatisRepository;
import com.adn.dabaeum.role.infrastructure.mybatis.UserRoleMyBatisRepository;
import com.adn.dabaeum.user.infrastructure.mybatis.UserMyBatisRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class DevProfileConfigurationTest {

    private static final List<Class<?>> DEV_COMPONENTS = List.of(
        RandomAttendanceIdGenerator.class,
        EphemeralAttendanceQrKeyProvider.class,
        AttendanceAdjustmentMyBatisRepository.class,
        AttendanceMyBatisRepository.class,
        AttendanceQrTokenMyBatisRepository.class,
        OpenApiContractController.class,
        DevBearerSecurityConfiguration.class,
        RandomCompletionIdGenerator.class,
        CompletionMyBatisRepository.class,
        CompletionOutboxMyBatisRepository.class,
        RandomCourseSessionIdGenerator.class,
        CourseInstructorMyBatisRepository.class,
        CourseMyBatisRepository.class,
        CourseSessionMyBatisRepository.class,
        RandomEnrollmentIdGenerator.class,
        EnrollmentMyBatisRepository.class,
        UserIdentityMyBatisRepository.class,
        InstitutionMyBatisRepository.class,
        UserRoleMyBatisRepository.class,
        UserMyBatisRepository.class
    );

    @Test
    void devProfileMustRegisterTheSameApplicationComponentsAsLocal() {
        DEV_COMPONENTS.forEach(component -> assertThat(profileValues(component))
            .as("@Profile on %s", component.getName())
            .contains("dev"));
    }

    @Test
    void devProfileMustNotActivateTheSshBackedDataSourceOrTunnel() {
        assertThat(profileValues(LocalDataSourceConfiguration.class))
            .containsExactly("local")
            .doesNotContain("dev");
        assertThat(profileValues(SshTunnelConfiguration.class))
            .containsExactly("local")
            .doesNotContain("dev");
    }

    @Test
    void devConfigurationMustUseDirectDatabaseAndExposeSwagger() throws Exception {
        String dev = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/application-dev.yml"));

        assertThat(dev)
            .contains("jdbc:postgresql://127.0.0.1:15432/dabaeum_dev")
            .doesNotContain("dabaeum.ssh-tunnel:")
            .contains("api-docs:")
            .contains("swagger-ui:")
            .contains("url: /openapi/dabaeum-api-v1.yaml");
    }

    private static String[] profileValues(Class<?> type) {
        Profile profile = type.getAnnotation(Profile.class);
        assertThat(profile)
            .as("@Profile on %s", type.getName())
            .isNotNull();
        return profile.value();
    }
}
