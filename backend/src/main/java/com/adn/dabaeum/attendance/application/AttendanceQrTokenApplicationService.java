package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface AttendanceQrTokenApplicationService {

    IssuedAttendanceQrToken issue(UUID sessionId, AuthenticatedUserContext context);

    ValidatedAttendanceQrToken validate(String rawToken, UUID expectedSessionId);
}
