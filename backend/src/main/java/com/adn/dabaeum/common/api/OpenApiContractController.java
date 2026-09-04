package com.adn.dabaeum.common.api;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/openapi")
public final class OpenApiContractController {

    private static final String CONTRACT_RESOURCE =
        "META-INF/dabaeum/openapi/dabaeum-api-v1.yaml";
    private static final String CONTRACT_URL =
        "/openapi/dabaeum-api-v1.yaml";
    private static final MediaType YAML_MEDIA_TYPE =
        MediaType.parseMediaType("application/yaml");

    @GetMapping(value = "/dabaeum-api-v1.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> contract() {
        ClassPathResource resource = new ClassPathResource(CONTRACT_RESOURCE);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(YAML_MEDIA_TYPE)
            .body(resource);
    }

    @GetMapping(value = "/swagger-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> swaggerConfig() {
        return ResponseEntity.ok(Map.of(
            "url", CONTRACT_URL,
            "validatorUrl", ""
        ));
    }
}
