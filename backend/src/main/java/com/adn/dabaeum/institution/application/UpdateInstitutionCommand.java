package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.util.UUID;

public record UpdateInstitutionCommand(
    UUID institutionId,
    UpdateField<String> institutionCode,
    UpdateField<String> name,
    UpdateField<String> businessNumber,
    UpdateField<String> representativeName,
    UpdateField<String> address,
    UpdateField<String> contactPhone,
    UpdateField<String> contactEmail,
    UpdateField<InstitutionStatus> status,
    UpdateField<String> homepageUrl,
    UpdateField<String> region,
    UpdateField<UUID> logoFileId,
    UpdateField<String> introduction
) {

    public UpdateInstitutionCommand(
        UUID institutionId,
        UpdateField<String> institutionCode,
        UpdateField<String> name,
        UpdateField<String> businessNumber,
        UpdateField<String> representativeName,
        UpdateField<String> address,
        UpdateField<String> contactPhone,
        UpdateField<String> contactEmail,
        UpdateField<InstitutionStatus> status,
        UpdateField<String> homepageUrl,
        UpdateField<String> region,
        UpdateField<UUID> logoFileId
    ) {
        this(institutionId, institutionCode, name, businessNumber, representativeName,
            address, contactPhone, contactEmail, status, homepageUrl, region,
            logoFileId, UpdateField.absent());
    }

    public int presentFieldCount() {
        return count(institutionCode)
            + count(name)
            + count(businessNumber)
            + count(representativeName)
            + count(address)
            + count(contactPhone)
            + count(contactEmail)
            + count(status)
            + count(homepageUrl)
            + count(region)
            + count(logoFileId)
            + count(introduction);
    }

    private int count(UpdateField<?> field) {
        return field != null && field.present() ? 1 : 0;
    }
}
