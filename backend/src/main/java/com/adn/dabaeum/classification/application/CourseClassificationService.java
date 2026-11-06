package com.adn.dabaeum.classification.application;

import com.adn.dabaeum.classification.domain.CourseClassification;
import com.adn.dabaeum.classification.domain.CourseClassificationRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표준 강좌 분류 (설계서 §12). 코드·이름은 마이그레이션이 정하고 여기서는 사용 여부만 바꾼다.
 * 표시 경로(pathOf)는 이름만 쓰므로 처음 한 번 읽어 둔다 — 이름은 마이그레이션(재배포)으로만 바뀐다.
 * 사용 가능 판정(requireAssignable)은 enabled 가 바뀔 수 있어 매번 DB 를 읽는다(77행).
 */
@Service
public class CourseClassificationService {

    private final CourseClassificationRepository repository;
    private volatile Map<String, CourseClassification> namesByCode;

    public CourseClassificationService(CourseClassificationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** enabledOnly 면 비활성 노드와 그 아래를 모두 뺀다. */
    @Transactional(readOnly = true)
    public List<CourseClassificationNode> tree(boolean enabledOnly) {
        List<CourseClassification> all = repository.findAll();
        return childrenOf(null, all, enabledOnly);
    }

    @Transactional
    public CourseClassificationNode setEnabled(String code, boolean enabled) {
        if (code == null || !repository.updateEnabled(code, enabled)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_CLASSIFICATION_NOT_FOUND,
                "Course classification not found");
        }
        List<CourseClassification> all = repository.findAll();
        CourseClassification node = byCode(all).get(code);
        return new CourseClassificationNode(node.code(), node.level(), node.name(), node.enabled(),
            childrenOf(code, all, false));
    }

    /** 강좌에 붙일 수 있는 코드인지: 존재, 자신과 조상 모두 활성, 하위 코드 없음. 아니면 422. */
    @Transactional(readOnly = true)
    public void requireAssignable(String code) {
        List<CourseClassification> all = repository.findAll();
        Map<String, CourseClassification> byCode = byCode(all);
        CourseClassification node = byCode.get(code);
        if (node == null || all.stream().anyMatch(other -> code.equals(other.parentCode()))) {
            throw invalid();
        }
        for (CourseClassification current = node; current != null; current = byCode.get(current.parentCode())) {
            if (!current.enabled()) {
                throw invalid();
            }
        }
    }

    /** 어느 단계든 존재하는 코드인지(사용 여부 무관). 배지 규칙처럼 "이 분류 아래" 를 가리킬 때 쓴다. */
    @Transactional(readOnly = true)
    public boolean exists(String code) {
        return code != null && repository.findAll().stream().anyMatch(node -> node.code().equals(code));
    }

    /** 위 단계부터 이름 목록. 코드가 없거나 모르는 코드면 빈 목록. */
    public List<String> pathOf(String code) {
        if (code == null) {
            return List.of();
        }
        Map<String, CourseClassification> names = namesByCode;
        if (names == null) {
            names = byCode(repository.findAll());
            namesByCode = names;
        }
        LinkedList<String> path = new LinkedList<>();
        for (CourseClassification current = names.get(code); current != null; current = names.get(current.parentCode())) {
            path.addFirst(current.name());
        }
        return List.copyOf(path);
    }

    private static List<CourseClassificationNode> childrenOf(
        String parentCode, List<CourseClassification> all, boolean enabledOnly
    ) {
        List<CourseClassificationNode> nodes = new ArrayList<>();
        all.stream()
            .filter(node -> Objects.equals(node.parentCode(), parentCode))
            .filter(node -> !enabledOnly || node.enabled())
            .sorted(Comparator.comparingInt(CourseClassification::sortOrder).thenComparing(CourseClassification::code))
            .forEach(node -> nodes.add(new CourseClassificationNode(
                node.code(), node.level(), node.name(), node.enabled(), childrenOf(node.code(), all, enabledOnly))));
        return nodes;
    }

    /** 1단계의 parentCode 는 null 이라 get(null) 이 가능한 HashMap 을 쓴다(불변 Map 은 NPE). */
    private static Map<String, CourseClassification> byCode(List<CourseClassification> all) {
        return all.stream().collect(Collectors.toMap(
            CourseClassification::code, Function.identity(), (left, right) -> left, HashMap::new));
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of("classificationCode"));
    }
}
