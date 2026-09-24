package com.studyhouse.spacz.partner.dto.response;

import java.util.List;

import com.studyhouse.spacz.partner.dto.LoginRef;
import com.studyhouse.spacz.partner.entity.Owner;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Owner with its properties (and their images, blocks and seats). Same fields as the legacy "
        + "API; nested records just don't point back to their parent.")
public record OwnerResponse(
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String ownerPhoneNumber,
        String address,
        LoginRef userLogin,
        List<PropertyItem> properties) {

    public static OwnerResponse from(Owner owner) {
        return new OwnerResponse(owner.getOwnerId(), owner.getOwnerName(), owner.getOwnerEmail(),
                owner.getOwnerPhoneNumber(), owner.getAddress(), LoginRef.of(owner.getLoginId()),
                owner.getProperties().stream().map(PropertyItem::from).toList());
    }
}
