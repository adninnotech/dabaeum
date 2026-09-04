package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.infrastructure.mybatis.LocalAccountMapper;
import com.adn.dabaeum.authentication.infrastructure.mybatis.LocalAccountMyBatisRepository;
import com.adn.dabaeum.authentication.infrastructure.mybatis.LocalAccountRow;
import com.adn.dabaeum.identity.api.IdentityResponse;
import com.adn.dabaeum.identity.infrastructure.mybatis.UserIdentityRow;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalAccountMyBatisRepositoryTest {

    private static final UUID IDENTITY_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID USER_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String EMAIL = "learner@example.com";
    private static final String PASSWORD_HASH = "$2a$12$test-password-hash";
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Mock
    LocalAccountMapper mapper;

    private LocalAccountMyBatisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new LocalAccountMyBatisRepository(mapper);
    }

    @Test
    void savesLocalCredentialWithoutExposingHashToIdentityModels() {
        LocalAccountCredential credential = credential();

        repository.save(credential);

        ArgumentCaptor<LocalAccountRow> captor = ArgumentCaptor.forClass(
            LocalAccountRow.class
        );
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LocalAccountRow(
            IDENTITY_ID,
            USER_ID,
            EMAIL,
            PASSWORD_HASH,
            NOW,
            NOW
        ));
        assertThat(recordComponentNames(UserIdentityRow.class))
            .doesNotContain("passwordHash");
        assertThat(recordComponentNames(IdentityResponse.class))
            .doesNotContain("passwordHash");
    }

    @Test
    void findsCredentialByNormalizedEmail() {
        LocalAccountRow row = new LocalAccountRow(
            IDENTITY_ID,
            USER_ID,
            EMAIL,
            PASSWORD_HASH,
            NOW,
            NOW
        );
        when(mapper.selectByNormalizedEmail(EMAIL)).thenReturn(row);

        assertThat(repository.findByNormalizedEmail(EMAIL))
            .contains(credential());
    }

    private LocalAccountCredential credential() {
        return new LocalAccountCredential(
            IDENTITY_ID,
            USER_ID,
            EMAIL,
            PASSWORD_HASH,
            NOW,
            NOW
        );
    }

    private java.util.List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
    }
}
