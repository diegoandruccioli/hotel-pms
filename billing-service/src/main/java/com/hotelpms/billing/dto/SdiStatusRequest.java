package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.SdiStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.NonNull;

/**
 * Request body for the PATCH /{id}/sdi-status endpoint.
 *
 * @param sdiStatus the new SDI status (must not be null)
 */
public record SdiStatusRequest(@NotNull @NonNull SdiStatus sdiStatus) {
}
