package com.construction.platform.dto;

import java.util.UUID;

public record RollbackRequest(
        UUID revisionId
) {
}
