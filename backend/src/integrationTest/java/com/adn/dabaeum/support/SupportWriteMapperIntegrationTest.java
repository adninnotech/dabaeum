package com.adn.dabaeum.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.FaqRepository;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsContentRepository;
import com.adn.dabaeum.support.domain.TermsType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * FAQ·약관 관리 CRUD 로 새로 추가된 매퍼 SQL(insert/update/delete)이 실제 PostgreSQL에서
 * 실행 가능한지 확인한다. 단위 테스트는 리포지토리를 mock으로 대체하므로 SQL 문법·컬럼명
 * 오류를 잡지 못한다.
 */
class SupportWriteMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    FaqRepository faqRepository;

    @Autowired
    TermsContentRepository termsRepository;

    @Test
    void savesUpdatesAndDeletesAFaq() {
        Faq faq = faq(uniqueQuestion());
        faqRepository.save(faq);

        assertThat(faqRepository.findById(faq.id())).contains(faq);

        Faq updated = new Faq(
            faq.id(), faq.question() + " (수정)", "수정된 답변", 5,
            faq.createdAt(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        assertThat(faqRepository.update(updated)).isTrue();
        assertThat(faqRepository.findById(faq.id())).contains(updated);

        assertThat(faqRepository.delete(faq.id())).isTrue();
        assertThat(faqRepository.findById(faq.id())).isEmpty();
    }

    @Test
    void updatingOrDeletingAnUnknownFaqReturnsFalse() {
        UUID unknownId = UUID.randomUUID();
        Faq unknown = faq(uniqueQuestion());
        Faq withUnknownId = new Faq(
            unknownId, unknown.question(), unknown.answer(), unknown.sortOrder(),
            unknown.createdAt(), unknown.updatedAt());

        assertThat(faqRepository.update(withUnknownId)).isFalse();
        assertThat(faqRepository.delete(unknownId)).isFalse();
    }

    @Test
    void savesANewTermsContentVersion() {
        String version = uniqueVersion();
        TermsContent terms = terms(TermsType.USAGE, version);

        termsRepository.save(terms);

        assertThat(termsRepository.findLatestByType(TermsType.USAGE))
            .map(TermsContent::version)
            .contains(version);
    }

    @Test
    void rejectsDuplicateTypeAndVersion() {
        String version = uniqueVersion();
        termsRepository.save(terms(TermsType.PRIVACY, version));

        assertThatThrownBy(() -> termsRepository.save(terms(TermsType.PRIVACY, version)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Faq faq(String question) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Faq(UUID.randomUUID(), question, "답변", 0, now, now);
    }

    private TermsContent terms(TermsType type, String version) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new TermsContent(
            UUID.randomUUID(), type, "IT테스트 약관", "본문", version, now, now);
    }

    private String uniqueQuestion() {
        return "IT테스트-FAQ-" + UUID.randomUUID();
    }

    private String uniqueVersion() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
