package com.adn.dabaeum.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FrontendApiGuideController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = ApiExceptionHandler.class
    )
)
@AutoConfigureMockMvc(addFilters = false)
class ProductionFrontendApiGuideControllerTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MockMvc mockMvc;

    @Test
    void doesNotExposeGuideWithoutLocalOrDevProfile() throws Exception {
        assertThat(applicationContext.getBeansOfType(
            FrontendApiGuideController.class)).isEmpty();
        mockMvc.perform(get("/api-guide/index.html"))
            .andExpect(status().isNotFound());
    }
}
