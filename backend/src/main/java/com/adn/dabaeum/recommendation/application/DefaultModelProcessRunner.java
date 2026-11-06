package com.adn.dabaeum.recommendation.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ProcessBuilder 로 실행한다. stderr 를 stdout 에 합쳐 별도 스레드가 끝까지 읽는다 — 읽지 않으면 파이프가 차서 자식이 멈춘다.
 * 출력은 로그에 쓰지 않고 마지막 줄(JSON 요약)만 돌려준다.
 */
public final class DefaultModelProcessRunner implements ModelProcessRunner {

    /** 프로세스가 끝난 뒤 출력 읽기 스레드를 기다리는 최대 시간. */
    private static final Duration OUTPUT_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public ProcessResult run(List<String> command, Map<String, String> environment, Duration timeout) {
        Process process = start(command, environment);
        AtomicReference<String> lastLine = new AtomicReference<>();
        Thread reader = Thread.ofVirtual().start(() -> drain(process, lastLine));
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            reader.join(OUTPUT_DRAIN_TIMEOUT);
            return new ProcessResult(finished ? process.exitValue() : -1, lastLine.get(), !finished);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model process wait interrupted", exception);
        }
    }

    private static Process start(List<String> command, Map<String, String> environment) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(environment);
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("model process could not start", exception);
        }
    }

    private static void drain(Process process, AtomicReference<String> lastLine) {
        try (BufferedReader output = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output.lines().filter(line -> !line.isBlank()).forEach(lastLine::set);
        } catch (IOException | UncheckedIOException ignored) {
            // 강제 종료로 파이프가 닫히면 여기로 온다. 그때까지 읽은 마지막 줄을 쓴다.
        }
    }
}
