package com.adn.dabaeum.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameMaskTest {

    @Test
    void 세_글자_이름은_가운데를_가린다() {
        assertThat(NameMask.mask("홍길동")).isEqualTo("홍*동");
    }

    @Test
    void 두_글자_이름은_뒤를_가린다() {
        assertThat(NameMask.mask("김철")).isEqualTo("김*");
    }

    @Test
    void 한_글자_이름은_그대로_가린다() {
        assertThat(NameMask.mask("김")).isEqualTo("*");
    }

    @Test
    void 네_글자_이상은_가운데를_모두_가린다() {
        assertThat(NameMask.mask("남궁민수")).isEqualTo("남**수");
        assertThat(NameMask.mask("스티븐호킹스")).isEqualTo("스****스");
    }

    @Test
    void null_과_공백은_그대로_돌려준다() {
        assertThat(NameMask.mask(null)).isNull();
        assertThat(NameMask.mask("")).isEmpty();
        assertThat(NameMask.mask("   ")).isEqualTo("   ");
    }
}
