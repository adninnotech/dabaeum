package com.adn.dabaeum.common.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.ProbeController.class)
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    RequestIdFilter.class,
    SecurityConfiguration.class,
    ApiExceptionHandlerTest.ProbeController.class
})
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void mapsApiExceptionToContractBody() throws Exception {
        mockMvc.perform(get("/probe/not-found").with(user("test-user")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"))
            .andExpect(jsonPath("$.details").isArray())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").value(
                matchesPattern(".*\\+09:00$")));
    }

    @Test
    void mapsMalformedJsonToBadRequestWithoutRawExceptionDetails() throws Exception {
        mockMvc.perform(post("/probe/validation")
                .with(user("test-user"))
                .contentType("application/json")
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.details").isArray())
            .andExpect(jsonPath("$.details").value(
                org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("at ")))));
    }

    @Test
    void mapsBeanValidationToUnprocessableEntityWithFieldOnlyDetails() throws Exception {
        mockMvc.perform(post("/probe/validation")
                .with(user("test-user"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[0]").value("name"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/not-found")
        ResponseEntity<Void> notFound() {
            throw new ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                ApiErrorCode.INSTITUTION_NOT_FOUND,
                "Institution not found"
            );
        }

        @PostMapping("/probe/validation")
        ResponseEntity<Void> validation(
            @Valid @RequestBody ValidatedRequest request
        ) {
            return ResponseEntity.ok().build();
        }
    }

    record ValidatedRequest(@NotBlank String name) {
    }
}
