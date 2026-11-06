package com.adn.dabaeum.classification.domain;

/**
 * 분류 코드를 "하위 포함" 접두사로 바꾼다(설계서 §12.3): TSIX_040000 → TSIX_04, TSIX_040200 → TSIX_0402, 3단계는 그대로.
 * 코드가 자릿수 기반이라 LIKE '접두사%' 한 번으로 그 분류와 하위 전부를 고른다.
 */
public final class ClassificationPrefix {

    public static final String CODE_PATTERN = "^TSIX_[0-9]{6}$";
    private static final int ROOT_LENGTH = "TSIX_00".length();

    private ClassificationPrefix() {
    }

    /** 형식이 틀리면 IllegalArgumentException. */
    public static String of(String code) {
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("classification code must be TSIX_AABBCC");
        }
        String prefix = code;
        while (prefix.length() > ROOT_LENGTH && prefix.endsWith("00")) {
            prefix = prefix.substring(0, prefix.length() - 2);
        }
        return prefix;
    }

    /** courseCode 가 ancestorCode 분류(하위 포함)에 속하는지. courseCode 가 없으면 false. */
    public static boolean contains(String ancestorCode, String courseCode) {
        return courseCode != null && courseCode.startsWith(of(ancestorCode));
    }
}
