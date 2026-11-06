package com.adn.dabaeum.recommendation.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 모델 학습 프로세스를 띄우고 끝날 때까지 기다린다 (§12.2). 테스트에서 가짜로 바꾸려고 분리했다. */
@FunctionalInterface
public interface ModelProcessRunner {

    /**
     * command 를 environment 를 더한 환경에서 실행한다. timeout 안에 끝나지 않으면 강제 종료하고 timedOut=true 로 돌려준다.
     * 실행 자체가 안 되면(파일 없음 등) 런타임 예외를 던진다.
     */
    ProcessResult run(List<String> command, Map<String, String> environment, Duration timeout);

    /**
     * @param exitCode 종료 코드. 시간 초과면 의미 없다
     * @param lastStdoutLine stdout(stderr 합침)의 비어 있지 않은 마지막 줄. 없으면 null
     * @param timedOut 시간 초과로 강제 종료했는지
     */
    record ProcessResult(int exitCode, String lastStdoutLine, boolean timedOut) {
    }
}
