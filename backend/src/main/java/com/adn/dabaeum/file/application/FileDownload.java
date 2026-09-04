package com.adn.dabaeum.file.application;

import com.adn.dabaeum.file.domain.StoredFile;
import java.io.InputStream;

/** 다운로드용 메타데이터와 열린 스트림. 호출자가 스트림을 닫는다. */
public record FileDownload(StoredFile file, InputStream content) {
}
