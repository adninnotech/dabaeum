package com.adn.dabaeum.common.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RoadmapNamingBoundaryTest {

    private static final Pattern ROADMAP_STAGE = Pattern.compile(
        "(?i)" + "stage" + "[ _-]?" + "6"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        ".java", ".yml", ".yaml", ".xml", ".sql", ".adoc", ".properties", ".gradle", ".sh", ".example"
    );

    @Test
    void activeArtifactsUseDomainNamesInsteadOfRoadmapNames() throws Exception {
        List<Path> files = new ArrayList<>();
        for (Path root : List.of(
            Path.of("src"),
            Path.of("scripts"),
            Path.of("docs/api")
        )) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                    .filter(RoadmapNamingBoundaryTest::isTextFile)
                    .forEach(files::add);
            }
        }
        files.add(Path.of("build.gradle"));

        List<String> violations = files.stream()
            .flatMap(path -> violations(path).stream())
            .sorted()
            .toList();

        assertThat(violations).isEmpty();
    }

    private static boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString();
        return TEXT_EXTENSIONS.stream().anyMatch(fileName::endsWith)
            || path.startsWith(Path.of("scripts"));
    }

    private static List<String> violations(Path path) {
        List<String> violations = new ArrayList<>();
        String relativePath = path.toString();
        if (ROADMAP_STAGE.matcher(relativePath).find()) {
            violations.add(relativePath + " (경로)");
        }
        try {
            if (ROADMAP_STAGE.matcher(Files.readString(path)).find()) {
                violations.add(relativePath + " (내용)");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("활성 파일을 읽을 수 없습니다: " + relativePath, exception);
        }
        return violations;
    }
}
