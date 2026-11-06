package com.adn.dabaeum.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 새로 저장하는 이메일은 도메인에 점이 있어야 한다(QA VAL-01, a@b 가입). 조회용 normalized 는 그대로 느슨하다. */
class EmailAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "a.b+tag@sub.example.co.kr", "홍길동@한국.kr"})
    void deliverableAcceptsDottedDomains(String email) {
        assertThat(EmailAddress.deliverable(email)).contains(email.toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void deliverableTrimsAndLowercasesLikeNormalized() {
        assertThat(EmailAddress.deliverable("  User@Example.COM ")).contains("user@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a@b", "a@b.", "a@.b", "a@b..c", "a b@c.de", "a@b c.de", "a@b@c.de", "@b.cd", ""})
    void deliverableRejectsDotlessOrBrokenAddresses(String email) {
        assertThat(EmailAddress.deliverable(email)).isEmpty();
    }

    @Test
    void deliverableRejectsOver320Characters() {
        String email = "a".repeat(311) + "@example.com";
        assertThat(email).hasSize(323);
        assertThat(EmailAddress.deliverable(email)).isEmpty();
        assertThat(EmailAddress.deliverable(null)).isEmpty();
    }

    @Test
    void normalizedStaysLenientForLookups() {
        // 이미 저장된 a@b 같은 계정도 로그인·재설정 조회는 된다. 새 저장만 막는다.
        assertThat(EmailAddress.normalized("A@B")).contains("a@b");
    }

    @Test
    void beanValidationPatternMatchesDeliverable() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (String bad : new String[] {"a@b", "a@b..c", "a@.b"}) {
            assertThat(validator.validate(new Sample(bad))).as(bad).isNotEmpty();
        }
        assertThat(validator.validate(new Sample("user@example.com"))).isEmpty();
        assertThat(validator.validate(new Sample(null))).isEmpty();
    }

    private record Sample(@Email(regexp = EmailAddress.PATTERN) String email) {
    }
}
