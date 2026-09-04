package com.adn.dabaeum.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

class DependencyCompatibilityTest {

    @Test
    void springBoot4AndThirdPartyApisCompile() {
        assertThat(WebMvcTest.class).isNotNull();
        assertThat(AutoConfigureRestDocs.class).isNotNull();
        assertThat(OpenApiValidationMatchers.openApi()).isNotNull();
    }
}
