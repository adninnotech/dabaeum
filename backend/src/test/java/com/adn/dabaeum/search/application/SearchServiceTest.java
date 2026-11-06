package com.adn.dabaeum.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.search.domain.SearchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §5.1 — 검색어 길이·limit 검증, LIKE 는 이스케이프·유사도는 원문 소문자. */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock SearchRepository repository;
    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(repository, Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void escapesLikeWildcardsButRanksByRawTerm() {
        when(repository.countCourses("100\\%\\_ai")).thenReturn(0L);

        SearchService.Result result = service.search("  100%_AI ", 5);

        verify(repository).courses("100\\%\\_ai", "100%_ai", 5);
        assertThat(result.courses().total()).isZero();
    }

    @Test
    void tooShortOrTooLongQueryIsBadRequest() {
        assertBadRequest(() -> service.search(" a ", 5));
        assertBadRequest(() -> service.search("가".repeat(101), 5));
        assertBadRequest(() -> service.search("교육", 21));
        verifyNoInteractions(repository);
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
    }
}
