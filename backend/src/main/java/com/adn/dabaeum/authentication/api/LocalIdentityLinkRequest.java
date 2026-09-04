package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 다대구 유저가 이메일·비밀번호를 추가로 설정할 때의 요청. 규칙은 회원가입과 같다. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record LocalIdentityLinkRequest(
    @NotBlank @Size(max = 320) String email,
    @NotBlank @Size(max = 72) String password
) {
}
