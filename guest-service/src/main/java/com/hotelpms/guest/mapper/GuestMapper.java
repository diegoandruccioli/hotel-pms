package com.hotelpms.guest.mapper;

import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.model.Guest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for the {@link Guest} entity and its request/response DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = IdentityDocumentMapper.class)
public interface GuestMapper {

    /**
     * Maps a {@link GuestRequest} to a {@link Guest} entity.
     *
     * @param request the incoming request DTO; must not be {@code null}
     * @return the mapped entity
     */
    Guest toEntity(GuestRequest request);

    /**
     * Maps a {@link Guest} entity to a {@link GuestResponse}.
     *
     * @param entity the guest entity; must not be {@code null}
     * @return the response DTO
     */
    GuestResponse toResponse(Guest entity);

    /**
     * Updates an existing {@link Guest} entity in-place from a
     * {@link GuestRequest}.
     * Identity, audit, and soft-delete fields are intentionally ignored.
     *
     * @param request the request DTO carrying the new values; must not be
     *                {@code null}
     * @param target  the entity to be mutated; must not be {@code null}
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "identityDocuments", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(GuestRequest request, @MappingTarget Guest target);
}
