package com.adn.dabaeum.file.application;

import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface FileApplicationService {

    StoredFile upload(UploadFileCommand command);

    FileDownload download(UUID fileId, AuthenticatedUserContext actor);
}
