package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.dto.LoginRef;
import com.studyhouse.spacz.partner.entity.Owner;

/** An owner shown as the parent of another record: all owner fields, without its property list. */
public record OwnerSummary(
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String ownerPhoneNumber,
        String address,
        LoginRef userLogin) {

    public static OwnerSummary from(Owner owner) {
        if (owner == null) {
            return null;
        }
        return new OwnerSummary(owner.getOwnerId(), owner.getOwnerName(), owner.getOwnerEmail(),
                owner.getOwnerPhoneNumber(), owner.getAddress(), LoginRef.of(owner.getLoginId()));
    }
}
