package com.hotelpms.guest.mapper;

import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.model.IdentityDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for IdentityDocument entity and DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentityDocumentMapper {

    /**
     * Maps request DTO to entity.
     *
     * @param request the request DTO
     * @return the entity
     */
    IdentityDocument toEntity(IdentityDocumentRequestDTO request);

    /**
     * Maps entity to response DTO.
     *
     * @param entity the entity
     * @return the response DTO
     */
    IdentityDocumentResponseDTO toResponse(IdentityDocument entity);

    /**
     * Updates entity from request DTO.
     *
     * @param request the request DTO
     * @param target  the target entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "guest", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(IdentityDocumentRequestDTO request, @MappingTarget IdentityDocument target);
}
