package com.adn.dabaeum.usage.application;

import com.adn.dabaeum.usage.domain.UsageEvent;
import java.util.Locale;
import java.util.regex.Pattern;

/** 검색어 정규화 (docs/specs/usage-statistics.md §3.3, §4.2). 개인정보로 보이는 검색어는 {@link #MASKED} 로 바꾼다. */
final class SearchKeyword {

    static final String MASKED = UsageEvent.MASKED_KEYWORD;
    static final int MAX_LENGTH = 50;
    /** 숫자가 이만큼 들어 있으면 전화번호로 본다. */
    static final int MASK_DIGITS = 6;

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchKeyword() {
    }

    static String normalize(String query) {
        String collapsed = SPACES.matcher(query == null ? "" : query.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
        long digits = collapsed.chars().filter(Character::isDigit).count();
        if (digits >= MASK_DIGITS || collapsed.indexOf('@') >= 0) {
            return MASKED;
        }
        if (collapsed.codePointCount(0, collapsed.length()) <= MAX_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, MAX_LENGTH));
    }
}
