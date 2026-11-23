package com.potatosmp.database;

import java.util.UUID;

public record BountyRecord(
        long id,
        UUID targetUuid,
        String targetName,
        long totalAmount,
        String status,
        long createdAt,
        long expiresAt,
        UUID claimedByUuid,
        String claimedByName,
        Long claimedAt
) {}
