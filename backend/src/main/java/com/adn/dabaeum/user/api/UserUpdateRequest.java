package com.adn.dabaeum.user.api;

import com.adn.dabaeum.user.application.UserUpdateField;
import com.adn.dabaeum.user.domain.Gender;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.adn.dabaeum.user.domain.EmailAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public class UserUpdateRequest {

    @Size(max = 100)
    private String name;

    @Email(regexp = EmailAddress.PATTERN)
    @Size(max = 320)
    private String email;

    @Size(max = 30)
    private String phone;

    private LocalDate birthDate;

    @Size(max = 2000)
    private String career;

    @Size(max = 2000)
    private String introduction;

    private UUID profileImageId;

    private Gender gender;

    private boolean namePresent;
    private boolean emailPresent;
    private boolean phonePresent;
    private boolean birthDatePresent;
    private boolean careerPresent;
    private boolean introductionPresent;
    private boolean profileImageIdPresent;
    private boolean genderPresent;

    public UserUpdateRequest() {
    }

    @JsonSetter("name")
    public void setName(String value) {
        namePresent = true;
        name = value;
    }

    @JsonSetter("email")
    public void setEmail(String value) {
        emailPresent = true;
        email = value;
    }

    @JsonSetter("phone")
    public void setPhone(String value) {
        phonePresent = true;
        phone = value;
    }

    @JsonSetter("birthDate")
    public void setBirthDate(LocalDate value) {
        birthDatePresent = true;
        birthDate = value;
    }

    public UserUpdateField<String> nameUpdate() {
        return field(namePresent, name);
    }

    public UserUpdateField<String> emailUpdate() {
        return field(emailPresent, email);
    }

    public UserUpdateField<String> phoneUpdate() {
        return field(phonePresent, phone);
    }

    public UserUpdateField<LocalDate> birthDateUpdate() {
        return field(birthDatePresent, birthDate);
    }

    @JsonSetter("career")
    public void setCareer(String value) {
        careerPresent = true;
        career = value;
    }

    @JsonSetter("introduction")
    public void setIntroduction(String value) {
        introductionPresent = true;
        introduction = value;
    }

    @JsonSetter("profileImageId")
    public void setProfileImageId(UUID value) {
        profileImageIdPresent = true;
        profileImageId = value;
    }

    public UserUpdateField<String> careerUpdate() {
        return field(careerPresent, career);
    }

    public UserUpdateField<String> introductionUpdate() {
        return field(introductionPresent, introduction);
    }

    public UserUpdateField<UUID> profileImageIdUpdate() {
        return field(profileImageIdPresent, profileImageId);
    }

    @JsonSetter("gender")
    public void setGender(Gender value) {
        genderPresent = true;
        gender = value;
    }

    public UserUpdateField<Gender> genderUpdate() {
        return field(genderPresent, gender);
    }

    private <T> UserUpdateField<T> field(boolean present, T value) {
        return present
            ? UserUpdateField.present(value)
            : UserUpdateField.absent();
    }
}
