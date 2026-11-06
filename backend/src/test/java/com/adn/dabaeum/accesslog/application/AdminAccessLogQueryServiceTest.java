package com.adn.dabaeum.accesslog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAccessLogQueryServiceTest {

    @Mock AdminAccessLogRepository repository;

    @Test
    void convertsSeoulDatesToAnInclusiveRange() {
        when(repository.search(any())).thenReturn(new AdminAccessLogPage(List.of(), 0));
        UUID actor = UUID.randomUUID();

        new AdminAccessLogQueryService(repository).list(
            1, 20, actor, AccessLogAction.READ, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28));

        ArgumentCaptor<AdminAccessLogCriteria> criteria = ArgumentCaptor.forClass(AdminAccessLogCriteria.class);
        verify(repository).search(criteria.capture());
        assertThat(criteria.getValue()).isEqualTo(new AdminAccessLogCriteria(
            actor, AccessLogAction.READ,
            Instant.parse("2026-08-31T15:00:00Z"), Instant.parse("2026-09-28T15:00:00Z"), 20, 20));
    }

    @Test
    void noFilterMeansWholePeriod() {
        when(repository.search(any())).thenReturn(new AdminAccessLogPage(List.of(), 0));

        new AdminAccessLogQueryService(repository).list(0, 10, null, null, null, null);

        verify(repository).search(new AdminAccessLogCriteria(null, null, null, null, 0, 10));
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> new AdminAccessLogQueryService(repository).list(
            0, 20, null, null, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                assertThat(exception.status().value()).isEqualTo(422);
            });
        verifyNoInteractions(repository);
    }

    @Test
    void invalidPageIsBadRequest() {
        AdminAccessLogQueryService service = new AdminAccessLogQueryService(repository);

        for (int[] paging : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.list(paging[0], paging[1], null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                    exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
        }
        verifyNoInteractions(repository);
    }
}
