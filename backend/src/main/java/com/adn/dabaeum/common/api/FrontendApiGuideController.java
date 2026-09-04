package com.adn.dabaeum.common.api;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api-guide")
public final class FrontendApiGuideController {

    private static final String RESOURCE_PREFIX =
        "META-INF/dabaeum/api-guide/";
    private static final String MARKDOWN_FILENAME =
        "dabaeum-frontend-api-flow.md";
    private static final MediaType CSS_MEDIA_TYPE =
        MediaType.parseMediaType("text/css");
    private static final MediaType HTML_MEDIA_TYPE =
        MediaType.parseMediaType("text/html;charset=UTF-8");
    private static final MediaType JAVASCRIPT_MEDIA_TYPE =
        MediaType.parseMediaType("text/javascript");
    private static final MediaType MARKDOWN_MEDIA_TYPE =
        MediaType.parseMediaType("text/markdown;charset=UTF-8");

    @GetMapping(value = "/index.html", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> index() {
        return inline("index.html", HTML_MEDIA_TYPE);
    }

    @GetMapping(value = "/api-guide.css", produces = "text/css")
    ResponseEntity<Resource> css() {
        return inline("api-guide.css", CSS_MEDIA_TYPE);
    }

    @GetMapping(value = "/api-guide.js", produces = "text/javascript")
    ResponseEntity<Resource> javascript() {
        return inline("api-guide.js", JAVASCRIPT_MEDIA_TYPE);
    }

    @GetMapping(
        value = "/dabaeum-frontend-api-flow.md",
        produces = "text/markdown;charset=UTF-8"
    )
    ResponseEntity<Resource> markdown() {
        ClassPathResource resource = resource(MARKDOWN_FILENAME);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(MARKDOWN_MEDIA_TYPE)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + MARKDOWN_FILENAME + "\""
            )
            .body(resource);
    }

    private ResponseEntity<Resource> inline(String name, MediaType mediaType) {
        ClassPathResource resource = resource(name);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(resource);
    }

    private ClassPathResource resource(String name) {
        return new ClassPathResource(RESOURCE_PREFIX + name);
    }
}
