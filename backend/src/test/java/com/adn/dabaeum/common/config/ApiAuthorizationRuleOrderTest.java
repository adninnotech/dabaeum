package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.config.ApiAuthorizationRules.Rule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 규칙표는 위에서 아래로 <b>먼저 맞는 것이 이긴다</b>.
 *
 * <p>{@link ApiAuthorizationRuleCoverageTest} 는 operation 마다 규칙이 <b>있는지</b>만 본다.
 * 어느 규칙이 <b>이기는지</b>는 안 보기 때문에, 와일드카드를 me 규칙 위에 얹으면 커버리지는
 * 그대로 통과하는데 학습자는 403 을 받는다 — {@code /users/*&#47;identities} 가
 * {@code /users/me/identities} 를 삼킨 전례가 있다. 여기서 순서를 고정한다.
 */
class ApiAuthorizationRuleOrderTest {

    private static final PathPatternParser PARSER = new PathPatternParser();

    /** 와일드카드 자리에 넣는 값. 규칙표의 어떤 리터럴 세그먼트와도 겹치면 안 된다. */
    private static final String ANY = "wildcard-segment";

    private static final List<HttpMethod> ALL_METHODS = List.of(
        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private record Match(int index, String pattern) {}

    /**
     * 모든 규칙은 <b>자기 패턴이 만드는 경로</b>에서 자기가 이겨야 한다. 앞 규칙이 먼저 잡으면
     * 그 규칙은 죽은 규칙이고, 죽은 이유는 대개 위쪽 와일드카드가 아래쪽 구체 경로를 삼켰기
     * 때문이다.
     */
    @Test
    void 뒤에_있는_규칙이_앞_규칙에_가려지지_않는다() {
        List<Rule> rules = ApiAuthorizationRules.rules();
        List<String> shadowed = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            List<HttpMethod> probes =
                rule.method() == null ? ALL_METHODS : List.of(rule.method());
            for (String pattern : rule.patterns()) {
                String path = samplePath(pattern);
                for (HttpMethod probe : probes) {
                    Match winner = firstMatch(rules, probe, path);
                    if (winner != null && winner.index() < i) {
                        shadowed.add(probe + " " + pattern
                            + " — 앞의 " + winner.pattern() + " 이(가) 먼저 잡는다");
                    }
                }
            }
        }

        assertThat(shadowed)
            .as("가려진 규칙 — 더 구체적인 규칙을 와일드카드 규칙보다 위로 올릴 것")
            .isEmpty();
    }

    private static Match firstMatch(List<Rule> rules, HttpMethod method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.method() != null && !rule.method().equals(method)) {
                continue;
            }
            for (String pattern : rule.patterns()) {
                if (PARSER.parse(pattern).matches(container)) {
                    return new Match(i, pattern);
                }
            }
        }
        return null;
    }

    /** 패턴이 실제로 받게 될 요청 경로 하나. {@code **} 를 먼저 지워야 {@code *} 치환이 안전하다. */
    private static String samplePath(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", ANY).replace("**", ANY).replace("*", ANY);
    }

    private static List<String> segments(String path) {
        return Arrays.asList(path.split("/"));
    }
}
