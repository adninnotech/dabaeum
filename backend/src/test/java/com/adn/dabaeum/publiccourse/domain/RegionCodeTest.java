package com.adn.dabaeum.publiccourse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegionCodeTest {

    @Test
    void codesMatchTheSpecTableInOrder() {
        assertThat(Arrays.stream(RegionCode.values()).map(RegionCode::code))
            .containsExactly("11", "14", "17", "20", "23", "26", "29", "71", "72", "99");
        assertThat(RegionCode.fromCode("14")).contains(RegionCode.DONG);
        assertThat(RegionCode.DONG.displayName()).isEqualTo("동구");
        assertThat(RegionCode.OTHER.displayName()).isEqualTo("기타");
    }

    @Test
    void unknownOrBlankCodesAreEmpty() {
        assertThat(RegionCode.fromCode("12")).isEmpty();
        assertThat(RegionCode.fromCode(null)).isEmpty();
        assertThat(RegionCode.fromCode(" 14 ")).contains(RegionCode.DONG);
    }
}
