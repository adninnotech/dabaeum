package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.application.AttendSessionView;
import com.adn.dabaeum.attendance.application.DadaeguAttendSessionService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 폰 전용. 강사 화면의 QR 이 이 주소를 가리키고, 폰 기본 카메라로 찍으면 여기로 온다.
 * 학생은 로그인하지 않는다 — 본인 확인은 다대구 DID 가, 현장 확인은 QR 토큰이 한다.
 *
 * <p>{@code /api/v1} 밖에 둔다. HTML 을 돌려주는 화면이지 프론트와 맺는 API 계약이 아니라서
 * OpenAPI 문서에 넣지 않는다. 같은 이유로 상태 응답도 {@code ApiResponse} 봉투를 쓰지 않는다.
 */
@RestController
@Profile({"local", "dev"})
public class DadaeguAttendController {

    private final DadaeguAttendSessionService service;

    public DadaeguAttendController(DadaeguAttendSessionService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping(value = "/attend", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> attend(@RequestParam(value = "t", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body(AttendPage.error(ApiErrorCode.ATTENDANCE_QR_INVALID.name()));
        }
        try {
            AttendSessionView view = service.open(token);
            return ResponseEntity.ok(AttendPage.render(view.id(), view.deepLink()));
        } catch (ApiException exception) {
            // 학생 화면이므로 JSON 오류 대신 읽을 수 있는 페이지를 준다. 상태 코드는 그대로 둔다.
            return ResponseEntity.status(exception.status())
                .contentType(MediaType.TEXT_HTML)
                .body(AttendPage.error(exception.code().name()));
        }
    }

    @GetMapping("/attend/{attendId}/status")
    public ResponseEntity<AttendStatusResponse> status(@PathVariable UUID attendId) {
        return service.status(attendId)
            .map(view -> ResponseEntity.ok(new AttendStatusResponse(
                view.state().name(), view.studentName(), view.errorCode())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 학생 폰이 2초마다 읽는다. 이름 외에 개인정보를 담지 않는다. */
    public record AttendStatusResponse(String state, String studentName, String errorCode) {
    }
}
