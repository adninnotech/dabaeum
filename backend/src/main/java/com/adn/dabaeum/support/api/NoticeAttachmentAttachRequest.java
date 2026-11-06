package com.adn.dabaeum.support.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record NoticeAttachmentAttachRequest(
    @NotNull UUID fileId
) {
}
