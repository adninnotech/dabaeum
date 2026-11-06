package com.adn.dabaeum.publiccourse.application.port;

import java.util.List;
import java.util.Map;

/** 공공데이터 강좌 원천 (설계서 §3.1). 실패는 {@code code} 를 가진 예외로 알린다. */
public interface PublicCourseSource {

    Page fetch(String insttCode, int pageNo, int numOfRows);

    /** 한 페이지 응답. 원본 행은 필드 순서를 유지하며 값은 문자열이다. */
    record Page(List<Map<String, String>> items, int totalCount) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
