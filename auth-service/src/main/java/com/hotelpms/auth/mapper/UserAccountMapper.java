package com.hotelpms.auth.mapper;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.RegisterRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for the {@link UserAccount} entity and related DTOs.
 */
@FunctionalInterface
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserAccountMapper {

    /**
     * Maps a {@link RegisterRequest} to a {@link UserAccount} entity.
     * The passwordHash is explicitly ignored as it will be encoded manually.
     *
     * @param request the registration request DTO
     * @return the mapped user account entity
     */
    @Mapping(target = "passwordHash", ignore = true)
    UserAccount toEntity(RegisterRequest request);
}
