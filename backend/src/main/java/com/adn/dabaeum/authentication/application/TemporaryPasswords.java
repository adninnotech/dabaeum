package com.adn.dabaeum.authentication.application;

import java.security.SecureRandom;

/** 관리자가 발급하는 임시 비밀번호. 헷갈리는 글자(I, O, l, 0, 1)를 뺀 14자. */
public final class TemporaryPasswords {

    private static final int LENGTH = 14;
    private static final char[] ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$".toCharArray();

    private TemporaryPasswords() {
    }

    public static String generate(SecureRandom random) {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
