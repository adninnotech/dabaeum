package com.adn.dabaeum.file.domain;

import java.io.InputStream;
import java.util.UUID;

/**
 * 파일 바이트를 보관하는 저장소 포트. 구현은 infrastructure에 둔다.
 *
 * <p>모든 경로는 저장소 루트 기준 상대경로이며 {@code <userId>/image/<uuid>.<ext>} 형태다.
 */
public interface FileStoragePort {

    /** 바이트를 저장하고 상대경로를 반환한다. */
    String store(UUID userId, String extension, InputStream content);

    /** 상대경로의 바이트를 연다. 호출자가 닫아야 한다. */
    InputStream open(String relativePath);

    /** 상대경로의 파일을 제거한다. 없으면 무시한다. */
    void delete(String relativePath);
}
