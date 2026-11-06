package com.adn.dabaeum.statistics.domain;

/** label 은 CONTENT(강좌 제목)·DEFINITION 만 DB 에서 오고, 나머지 축은 서비스가 key 로 만든다. */
public record EnrollmentStatRow(String key, String label, long applied, long approved, long completed) {

    public EnrollmentStatRow withLabel(String value) {
        return new EnrollmentStatRow(key, value, applied, approved, completed);
    }
}
