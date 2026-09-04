package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.application.UpdateField;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

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

    @Email
    @Size(max = 320)
    private String contactEmail;

    private InstitutionStatus status;

    private boolean institutionCodePresent;
    private boolean namePresent;
    private boolean businessNumberPresent;
    private boolean representativeNamePresent;
    private boolean addressPresent;
    private boolean contactPhonePresent;
    private boolean contactEmailPresent;
    private boolean statusPresent;

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

    private <T> UpdateField<T> field(boolean present, T value) {
        return present ? UpdateField.present(value) : UpdateField.absent();
    }
}
