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
    UpdateField<InstitutionStatus> status
) {

    public int presentFieldCount() {
        return count(institutionCode)
            + count(name)
            + count(businessNumber)
            + count(representativeName)
            + count(address)
            + count(contactPhone)
            + count(contactEmail)
            + count(status);
    }

    private int count(UpdateField<?> field) {
        return field != null && field.present() ? 1 : 0;
    }
}
