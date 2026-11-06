package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 수강신청 일괄 승인·반려.
 *
 * <p>건별 {@code approve}·{@code reject} 를 프록시(인터페이스)로 다시 호출하는 별도 빈이다.
 * {@code DefaultEnrollmentApplicationService} 안에서 {@code this.approve(...)} 를 부르면
 * 프록시를 타지 않아 건별 {@code @Transactional} 이 조용히 사라진다.
 */
@Service
public class EnrollmentBulkDecisionService {

    /** 한 번에 처리할 건수 상한. 목록 한 페이지가 100건이라 그 이상은 화면에서 고를 수 없다. */
    private static final int MAX_BULK_SIZE = 100;

    private final EnrollmentApplicationService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentDecisionAuthority decisionAuthority;

    public EnrollmentBulkDecisionService(
        EnrollmentApplicationService enrollmentService,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        EnrollmentDecisionAuthority decisionAuthority
    ) {
        this.enrollmentService = Objects.requireNonNull(enrollmentService, "enrollmentService");
        this.enrollmentRepository =
            Objects.requireNonNull(enrollmentRepository, "enrollmentRepository");
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
        this.decisionAuthority =
            Objects.requireNonNull(decisionAuthority, "decisionAuthority");
    }

    /**
     * 여기에는 일부러 {@code @Transactional} 을 걸지 않는다. 건별 호출이 각자의 트랜잭션에서
     * 돌아야 실패한 건만 롤백되고, {@code approve} 안의 {@code FOR UPDATE} 잠금도 짧게 끝난다.
     * 바깥에 트랜잭션을 걸면 잡아 삼킨 {@code ApiException} 이 트랜잭션을 rollback-only 로
     * 만들어 성공한 건까지 전부 되돌아간다.
     */
    public BulkDecisionResult bulkDecide(
        BulkDecisionCommand command,
        AuthenticatedUserContext context
    ) {
        Set<UUID> ids = requireIds(command);
        if (command.decision() == BulkDecisionCommand.Decision.REJECT
            && (command.reason() == null || command.reason().isBlank())) {
            throw validation("reason");
        }

        // 권한은 먼저 한 번에 검사한다. 남의 기관 건이 섞였다는 것은 화면이 잘못됐거나 공격이므로
        // 부분 성공으로 넘기지 않고 아무것도 건드리지 않는다.
        List<Enrollment> targets = enrollmentRepository.findAllByIds(List.copyOf(ids));
        if (targets.size() != ids.size()) {
            throw notFound(ApiErrorCode.ENROLLMENT_NOT_FOUND, "Enrollment not found");
        }
        for (Enrollment target : targets) {
            // 강좌가 지워진 건은 여기서 막지 않는다. 건별 approve/reject 가 아무것도 바꾸지 않고
            // COURSE_NOT_FOUND 로 실패 목록에 넣으므로, 그 한 건 때문에 배치 전체를 404 로
            // 죽이면 부분 성공 원칙에 어긋난다. 권한은 강좌가 있는 건에 대해서만 물을 수 있다.
            courseRepository.findActiveById(target.courseId())
                .ifPresent(course -> decisionAuthority.require(context, course));
        }

        List<UUID> succeeded = new ArrayList<>();
        List<BulkDecisionResult.Failure> failed = new ArrayList<>();
        for (Enrollment target : targets) {
            try {
                decide(command, target.id(), context);
                succeeded.add(target.id());
            } catch (ApiException exception) {
                // 정원 초과는 여기서 자연스럽게 걸린다 — approve 가 건마다 과정 행을 잠그고
                // 정원을 다시 세므로 넘치는 건만 실패 목록으로 떨어진다.
                failed.add(new BulkDecisionResult.Failure(target.id(),
                    exception.code().name() + ": " + exception.getMessage()));
            }
        }
        return new BulkDecisionResult(List.copyOf(succeeded), List.copyOf(failed));
    }

    private void decide(
        BulkDecisionCommand command,
        UUID enrollmentId,
        AuthenticatedUserContext context
    ) {
        if (command.decision() == BulkDecisionCommand.Decision.APPROVE) {
            enrollmentService.approve(enrollmentId, context);
        } else {
            enrollmentService.reject(
                new RejectEnrollmentCommand(enrollmentId, command.reason()), context);
        }
    }

    private Set<UUID> requireIds(BulkDecisionCommand command) {
        if (command == null || command.decision() == null || command.enrollmentIds() == null
            || command.enrollmentIds().isEmpty()
            || command.enrollmentIds().size() > MAX_BULK_SIZE
            || command.enrollmentIds().stream().anyMatch(Objects::isNull)) {
            throw validation("enrollmentIds");
        }
        return new LinkedHashSet<>(command.enrollmentIds());
    }

    private ApiException validation(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(detail));
    }

    private ApiException notFound(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
