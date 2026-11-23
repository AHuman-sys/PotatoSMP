package com.potatosmp.util;

import org.bukkit.Material;

import java.util.Optional;

/**
 * Pure, deterministic domain logic for PotatoSMP calculations and validations.
 * Decoupled from Bukkit runtime state so it can be thoroughly unit tested.
 */
public final class CoreLogic {

    private CoreLogic() {}

    /**
     * Calculates credit payout for a potato deposit using current rate and rounding down safely,
     * guaranteeing non-negative payouts and preventing overflow.
     */
    public static long calculateDepositCredits(int regularPotatoes, double regularRate,
                                               int goldenPotatoes, double goldenRate,
                                               int poisonousPotatoes, double poisonousRate) {
        if (regularPotatoes < 0 || goldenPotatoes < 0 || poisonousPotatoes < 0) {
            throw new IllegalArgumentException("Potato counts cannot be negative");
        }
        double total = (regularPotatoes * Math.max(0.0, regularRate))
                + (goldenPotatoes * Math.max(0.0, goldenRate))
                + (poisonousPotatoes * Math.max(0.0, poisonousRate));
        if (total <= 0.0) {
            return 0L;
        }
        return (long) Math.floor(total);
    }

    /**
     * Computes next bounded market multiplier using a symmetric step delta.
     */
    public static double nextMarketMultiplier(double current, double randomUnitMinusOneToOne,
                                              double maxStepChange, double minMultiplier, double maxMultiplier) {
        double clampedMin = Math.max(0.1, minMultiplier);
        double clampedMax = Math.max(clampedMin, maxMultiplier);
        double delta = Math.max(-1.0, Math.min(1.0, randomUnitMinusOneToOne)) * Math.max(0.0, maxStepChange);
        // Gentle mean-reversion pull toward 1.00 so rates stay balanced long-term
        double reversion = (1.0 - current) * 0.15;
        double next = current + delta + reversion;
        return Math.max(clampedMin, Math.min(clampedMax, Math.round(next * 1000.0) / 1000.0));
    }

    /**
     * Maps raw ore / smeltable block drops for Potato Pickaxe auto-smelt.
     */
    public static Optional<Material> getSmeltedResult(Material source, boolean smeltStoneToSmooth) {
        if (source == null) {
            return Optional.empty();
        }
        return switch (source) {
            case RAW_IRON -> Optional.of(Material.IRON_INGOT);
            case RAW_GOLD -> Optional.of(Material.GOLD_INGOT);
            case RAW_COPPER -> Optional.of(Material.COPPER_INGOT);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Optional.of(Material.IRON_INGOT);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Optional.of(Material.GOLD_INGOT);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Optional.of(Material.COPPER_INGOT);
            case SAND, RED_SAND -> Optional.of(Material.GLASS);
            case CLAY_BALL -> Optional.of(Material.BRICK);
            case NETHERRACK -> Optional.of(Material.NETHER_BRICK);
            case COBBLESTONE -> smeltStoneToSmooth ? Optional.of(Material.STONE) : Optional.empty();
            case STONE -> smeltStoneToSmooth ? Optional.of(Material.SMOOTH_STONE) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Evaluates whether a Netherite material should be blocked by the Netherite restriction system.
     */
    public static boolean isBannedNetheriteEquipment(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS,
                 NETHERITE_SWORD,
                 NETHERITE_AXE,
                 NETHERITE_PICKAXE,
                 NETHERITE_SHOVEL,
                 NETHERITE_HOE -> true;
            default -> false;
        };
    }

    /**
     * Maps banned Netherite equipment to its Diamond equivalent for optional CONVERT restriction mode.
     */
    public static Optional<Material> downgradeNetheriteToDiamond(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        return switch (material) {
            case NETHERITE_HELMET -> Optional.of(Material.DIAMOND_HELMET);
            case NETHERITE_CHESTPLATE -> Optional.of(Material.DIAMOND_CHESTPLATE);
            case NETHERITE_LEGGINGS -> Optional.of(Material.DIAMOND_LEGGINGS);
            case NETHERITE_BOOTS -> Optional.of(Material.DIAMOND_BOOTS);
            case NETHERITE_SWORD -> Optional.of(Material.DIAMOND_SWORD);
            case NETHERITE_AXE -> Optional.of(Material.DIAMOND_AXE);
            case NETHERITE_PICKAXE -> Optional.of(Material.DIAMOND_PICKAXE);
            case NETHERITE_SHOVEL -> Optional.of(Material.DIAMOND_SHOVEL);
            case NETHERITE_HOE -> Optional.of(Material.DIAMOND_HOE);
            default -> Optional.empty();
        };
    }

    /**
     * Validates anti-farming eligibility between killer and victim.
     */
    public static boolean isBountyClaimAllowed(long nowMillis, long lastClaimedMillis,
                                               long cooldownMillis, int claimsInLast24h, int maxClaimsPerDay) {
        if (lastClaimedMillis > 0 && cooldownMillis > 0 && (nowMillis - lastClaimedMillis) < cooldownMillis) {
            return false;
        }
        return maxClaimsPerDay <= 0 || claimsInLast24h < maxClaimsPerDay;
    }

    /**
     * Validates whether a Potato Heart can be consumed given current usage and configured max limit.
     */
    public static boolean canConsumePotatoHeart(int usedHearts, int maxHearts) {
        return maxHearts > 0 && usedHearts >= 0 && usedHearts < maxHearts;
    }

    /**
     * Calculates bonus potato count for Potato Hoe within [min, max] when drop check succeeds.
     */
    public static int calculateHoeBonusPotatoes(int minBonus, int maxBonus, double roll, double dropChance, double randomUnit) {
        if (roll > dropChance || maxBonus <= 0) {
            return 0;
        }
        int low = Math.max(0, minBonus);
        int high = Math.max(low, maxBonus);
        int span = (high - low) + 1;
        int offset = Math.min(span - 1, Math.max(0, (int) Math.floor(randomUnit * span)));
        return low + offset;
    }
}
