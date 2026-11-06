package com.adn.dabaeum.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RichTextTest {

    @Test
    void null_은_그대로_null() {
        assertThat(RichText.sanitize(null)).isNull();
    }

    @Test
    void 허용된_서식은_남긴다() {
        String html = "<p>안녕하세요 <strong>굵게</strong> <em>기울임</em></p><ul><li>항목</li></ul>";

        assertThat(RichText.sanitize(html))
            .contains("<strong>굵게</strong>")
            .contains("<li>항목</li>");
    }

    @Test
    void script_태그를_제거한다() {
        String html = "<p>본문</p><script>alert('xss')</script>";

        assertThat(RichText.sanitize(html))
            .contains("본문")
            .doesNotContain("script")
            .doesNotContain("alert");
    }

    @Test
    void onerror_같은_이벤트_속성을_제거한다() {
        String html = "<img src=\"x\" onerror=\"alert(1)\">";

        assertThat(RichText.sanitize(html)).doesNotContain("onerror");
    }

    @Test
    void javascript_스킴_링크를_제거한다() {
        String html = "<a href=\"javascript:alert(1)\">클릭</a>";

        assertThat(RichText.sanitize(html)).doesNotContain("javascript:");
    }

    @Test
    void 평범한_링크와_이미지는_남긴다() {
        String html = "<a href=\"https://dabaeum.kr\">링크</a>"
            + "<img src=\"https://dabaeum.kr/a.png\">";

        assertThat(RichText.sanitize(html))
            .contains("https://dabaeum.kr")
            .contains("<img");
    }

    @Test
    void 공백만_있으면_그대로_돌려준다() {
        assertThat(RichText.sanitize("   ")).isEqualTo("   ");
    }

    @Test
    void target_blank_이고_rel_이_없으면_noopener_noreferrer_가_강제된다() {
        String html = "<a href=\"https://attacker.example\" target=\"_blank\">클릭</a>";

        String result = RichText.sanitize(html);

        assertThat(result).contains("noopener").contains("noreferrer");
    }

    @Test
    void target_blank_인데_rel_이_이미_있어도_noopener_가_보장된다() {
        String html = "<a href=\"https://attacker.example\" target=\"_blank\" rel=\"nofollow\">클릭</a>";

        String result = RichText.sanitize(html);

        assertThat(result).contains("noopener");
    }

    @Test
    void target_이_없는_링크는_rel_이_강제로_붙지_않는다() {
        String html = "<a href=\"https://dabaeum.kr\">링크</a>";

        assertThat(RichText.sanitize(html)).doesNotContain("rel=");
    }
}
