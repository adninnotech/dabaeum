package com.adn.dabaeum.statistics.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 엑셀에서 한글이 깨지지 않게 UTF-8 BOM 을 붙인 CSV (설계서 §9.2). 값은 모두 따옴표로 감싼다. */
public final class StatisticsCsv {

    private static final String BOM = "﻿";

    private StatisticsCsv() {
    }

    public static byte[] write(List<String> header, List<List<Object>> rows) {
        StringBuilder csv = new StringBuilder(BOM);
        line(csv, header.stream().map(Object.class::cast).toList());
        rows.forEach(row -> line(csv, row));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder csv, List<Object> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            Object value = values.get(index);
            String text = value == null ? "" : value.toString();
            // 수식 주입 방지: 셀이 =, +, -, @ 로 시작하면 앞에 작은따옴표를 붙인다.
            if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
                text = "'" + text;
            }
            csv.append('"').append(text.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
