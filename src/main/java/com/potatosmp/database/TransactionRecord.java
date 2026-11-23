package com.potatosmp.database;

import java.util.UUID;

public record TransactionRecord(
        long id,
        UUID playerUuid,
        TransactionType type,
        long creditDelta,
        int potatoCount,
        String details,
        long createdAt
) {}
