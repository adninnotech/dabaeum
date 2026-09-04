package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * didLogin.js 콜백이 받은 returnData 의 암호화 필드(RSA 암호문 Base64) 그대로.
 * requiredVC=Name:PhoneNum:Birthdate — ci·gender·isForeigner 는 와도 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DadaeguLoginRequest(
    @NotBlank @Size(max = 4096) String did,
    @Size(max = 4096) String name,
    @Size(max = 4096) String birthdate,
    @Size(max = 4096) String phoneNumber
) {
}
