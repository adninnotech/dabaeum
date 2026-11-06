package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.application.UpdateField;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.adn.dabaeum.user.domain.EmailAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public class InstitutionUpdateRequest {

    @Size(max = 50)
    private String institutionCode;

    @Size(max = 200)
    private String name;

    @Size(max = 20)
    private String businessNumber;

    @Size(max = 100)
    private String representativeName;

    private String address;

    @Size(max = 30)
    private String contactPhone;

    @Email(regexp = EmailAddress.PATTERN)
    @Size(max = 320)
    private String contactEmail;

    private InstitutionStatus status;

    @Size(max = 500)
    private String homepageUrl;

    @Size(max = 50)
    private String region;

    private UUID logoFileId;

    @Size(max = 2000)
    private String introduction;

    private boolean institutionCodePresent;
    private boolean namePresent;
    private boolean businessNumberPresent;
    private boolean representativeNamePresent;
    private boolean addressPresent;
    private boolean contactPhonePresent;
    private boolean contactEmailPresent;
    private boolean statusPresent;
    private boolean homepageUrlPresent;
    private boolean regionPresent;
    private boolean logoFileIdPresent;
    private boolean introductionPresent;

    public InstitutionUpdateRequest() {
    }

    @JsonSetter("institutionCode")
    public void setInstitutionCode(String value) {
        institutionCodePresent = true;
        institutionCode = value;
    }

    @JsonSetter("name")
    public void setName(String value) {
        namePresent = true;
        name = value;
    }

    @JsonSetter("businessNumber")
    public void setBusinessNumber(String value) {
        businessNumberPresent = true;
        businessNumber = value;
    }

    @JsonSetter("representativeName")
    public void setRepresentativeName(String value) {
        representativeNamePresent = true;
        representativeName = value;
    }

    @JsonSetter("address")
    public void setAddress(String value) {
        addressPresent = true;
        address = value;
    }

    @JsonSetter("contactPhone")
    public void setContactPhone(String value) {
        contactPhonePresent = true;
        contactPhone = value;
    }

    @JsonSetter("contactEmail")
    public void setContactEmail(String value) {
        contactEmailPresent = true;
        contactEmail = value;
    }

    @JsonSetter("status")
    public void setStatus(InstitutionStatus value) {
        statusPresent = true;
        status = value;
    }

    @JsonSetter("homepageUrl")
    public void setHomepageUrl(String value) {
        homepageUrlPresent = true;
        homepageUrl = value;
    }

    @JsonSetter("region")
    public void setRegion(String value) {
        regionPresent = true;
        region = value;
    }

    @JsonSetter("logoFileId")
    public void setLogoFileId(UUID value) {
        logoFileIdPresent = true;
        logoFileId = value;
    }

    @JsonSetter("introduction")
    public void setIntroduction(String value) {
        introductionPresent = true;
        introduction = value;
    }

    public boolean hasOnlyInstitutionProfileFields() {
        return !institutionCodePresent && !namePresent && !businessNumberPresent
            && !representativeNamePresent && !addressPresent && !statusPresent
            && !homepageUrlPresent && !regionPresent;
    }

    public UpdateField<String> institutionCodeUpdate() {
        return field(institutionCodePresent, institutionCode);
    }

    public UpdateField<String> nameUpdate() {
        return field(namePresent, name);
    }

    public UpdateField<String> businessNumberUpdate() {
        return field(businessNumberPresent, businessNumber);
    }

    public UpdateField<String> representativeNameUpdate() {
        return field(representativeNamePresent, representativeName);
    }

    public UpdateField<String> addressUpdate() {
        return field(addressPresent, address);
    }

    public UpdateField<String> contactPhoneUpdate() {
        return field(contactPhonePresent, contactPhone);
    }

    public UpdateField<String> contactEmailUpdate() {
        return field(contactEmailPresent, contactEmail);
    }

    public UpdateField<InstitutionStatus> statusUpdate() {
        return field(statusPresent, status);
    }

    public UpdateField<String> homepageUrlUpdate() {
        return field(homepageUrlPresent, homepageUrl);
    }

    public UpdateField<String> regionUpdate() {
        return field(regionPresent, region);
    }

    public UpdateField<UUID> logoFileIdUpdate() {
        return field(logoFileIdPresent, logoFileId);
    }

    public UpdateField<String> introductionUpdate() {
        return field(introductionPresent, introduction);
    }

    private <T> UpdateField<T> field(boolean present, T value) {
        return present ? UpdateField.present(value) : UpdateField.absent();
    }
}
