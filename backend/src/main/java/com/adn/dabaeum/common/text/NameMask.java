package com.adn.dabaeum.common.text;

/** 공개 화면에 이름을 내보낼 때 쓴다. 서버가 가려서 보내야 원본이 네트워크를 타지 않는다. */
public final class NameMask {

    private NameMask() {
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        int length = name.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(length - 2) + name.charAt(length - 1);
    }
}
