package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class VcPublicResourceService {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

    private final CredentialUriProvider uriProvider;

    public VcPublicResourceService(CredentialUriProvider uriProvider) {
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
    }

    public Map<String, Object> contextDocument() {
        String vocabulary = uriProvider.vocabularyUrl() + "#";
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("@protected", true);
        context.put("LifelongEducationCompletionCredential",
            vocabulary + "LifelongEducationCompletionCredential");
        context.put("DabaeumCredentialStatus", vocabulary + "DabaeumCredentialStatus");
        context.put("completionId", vocabulary + "completionId");
        context.put("enrollmentId", vocabulary + "enrollmentId");
        context.put("courseId", vocabulary + "courseId");
        context.put("completedAt", typed(vocabulary + "completedAt", XSD + "dateTime"));
        context.put("attendanceRate", typed(vocabulary + "attendanceRate", XSD + "decimal"));
        context.put("completedMinutes", typed(vocabulary + "completedMinutes", XSD + "integer"));
        context.put("creditValue", typed(vocabulary + "creditValue", XSD + "decimal"));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", context);
        return document;
    }

    public Map<String, Object> vocabularyDocument() {
        String vocabulary = uriProvider.vocabularyUrl();
        List<Map<String, String>> terms = new ArrayList<>();
        terms.add(term(vocabulary, "LifelongEducationCompletionCredential",
            "평생교육 과정 이수 증명 Credential", "type"));
        terms.add(term(vocabulary, "DabaeumCredentialStatus",
            "다배움 Credential 상태 참조", "type"));
        terms.add(term(vocabulary, "completionId", "이수 결과 식별자", "UUID"));
        terms.add(term(vocabulary, "enrollmentId", "수강 신청 식별자", "UUID"));
        terms.add(term(vocabulary, "courseId", "과정 식별자", "UUID"));
        terms.add(term(vocabulary, "completedAt", "이수 확정 시각", "xsd:dateTime"));
        terms.add(term(vocabulary, "attendanceRate", "출석률", "xsd:decimal (0.00~100.00)"));
        terms.add(term(vocabulary, "completedMinutes", "이수 시간(분)", "xsd:integer (0 이상)"));
        terms.add(term(vocabulary, "creditValue", "학점 값", "xsd:decimal (0.00 이상)"));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", vocabulary);
        document.put("version", vocabulary.substring(vocabulary.lastIndexOf('/') + 1));
        document.put("terms", List.copyOf(terms));
        return document;
    }

    private Map<String, String> typed(String id, String type) {
        Map<String, String> definition = new LinkedHashMap<>();
        definition.put("@id", id);
        definition.put("@type", type);
        return definition;
    }

    private Map<String, String> term(
        String vocabulary, String name, String description, String valueFormat
    ) {
        Map<String, String> term = new LinkedHashMap<>();
        term.put("term", name);
        term.put("id", vocabulary + "#" + name);
        term.put("description", description);
        term.put("valueFormat", valueFormat);
        return term;
    }
}
