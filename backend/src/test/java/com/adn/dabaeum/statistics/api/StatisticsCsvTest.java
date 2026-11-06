package com.adn.dabaeum.statistics.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsCsvTest {

    @Test
    void startsWithBomQuotesValuesAndDefusesFormulas() {
        String csv = new String(StatisticsCsv.write(List.of("key", "label"),
            List.of(List.of("1", "문화 \"교실\""), List.of("2", "=cmd()"))), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿\"key\",\"label\"\r\n");
        assertThat(csv).contains("\"문화 \"\"교실\"\"\"").contains("\"'=cmd()\"");
    }
}
