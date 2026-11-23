package com.potatosmp.database;

import java.util.UUID;

public record BountyContribution(
        long id,
        long bountyId,
        UUID contributorUuid,
        String contributorName,
        long amount,
        long contributedAt
) {}
