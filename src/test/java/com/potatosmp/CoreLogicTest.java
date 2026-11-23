package com.potatosmp;

import com.potatosmp.util.CoreLogic;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreLogicTest {

    @Test
    void depositCalculationCombinesRegularGoldenAndPoisonous() {
        long credits = CoreLogic.calculateDepositCredits(64, 2.5, 2, 120.0, 4, 0.5);
        // 64*2.5 = 160, 2*120 = 240, 4*0.5 = 2 -> 402
        assertEquals(402L, credits);
    }

    @Test
    void depositCalculationRejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> CoreLogic.calculateDepositCredits(-1, 2.0, 0, 120.0, 0, 0.0));
    }

    @Test
    void marketMultiplierStaysWithinConfiguredBounds() {
        double high = CoreLogic.nextMarketMultiplier(1.19, 1.0, 0.15, 0.85, 1.20);
        double low = CoreLogic.nextMarketMultiplier(0.86, -1.0, 0.15, 0.85, 1.20);
        assertEquals(1.20, high, 0.0001);
        assertEquals(0.85, low, 0.0001);
    }

    @Test
    void pickaxeAutoSmeltMapsRawOresAndRespectsStoneConfig() {
        assertEquals(Material.IRON_INGOT, CoreLogic.getSmeltedResult(Material.RAW_IRON, false).orElseThrow());
        assertEquals(Material.GOLD_INGOT, CoreLogic.getSmeltedResult(Material.RAW_GOLD, false).orElseThrow());
        assertEquals(Material.COPPER_INGOT, CoreLogic.getSmeltedResult(Material.RAW_COPPER, false).orElseThrow());
        assertTrue(CoreLogic.getSmeltedResult(Material.COBBLESTONE, false).isEmpty());
        assertEquals(Material.STONE, CoreLogic.getSmeltedResult(Material.COBBLESTONE, true).orElseThrow());
    }

    @Test
    void netheriteBanDetectsAllEquipmentAndDowngradesCorrectly() {
        assertTrue(CoreLogic.isBannedNetheriteEquipment(Material.NETHERITE_SWORD));
        assertTrue(CoreLogic.isBannedNetheriteEquipment(Material.NETHERITE_CHESTPLATE));
        assertFalse(CoreLogic.isBannedNetheriteEquipment(Material.DIAMOND_SWORD));
        assertEquals(Material.DIAMOND_PICKAXE,
                CoreLogic.downgradeNetheriteToDiamond(Material.NETHERITE_PICKAXE).orElseThrow());
    }

    @Test
    void bountyAntiFarmBlocksCooldownAndDailyCap() {
        long now = 10_000_000L;
        long cooldown = 3_600_000L; // 60 min
        // Within cooldown -> blocked
        assertFalse(CoreLogic.isBountyClaimAllowed(now, now - 1_000_000L, cooldown, 0, 2));
        // Past cooldown & under daily cap -> allowed
        assertTrue(CoreLogic.isBountyClaimAllowed(now, now - 4_000_000L, cooldown, 1, 2));
        // Past cooldown but reached daily cap -> blocked
        assertFalse(CoreLogic.isBountyClaimAllowed(now, now - 4_000_000L, cooldown, 2, 2));
    }

    @Test
    void potatoHeartRespectsMaxLimit() {
        assertTrue(CoreLogic.canConsumePotatoHeart(0, 8));
        assertTrue(CoreLogic.canConsumePotatoHeart(7, 8));
        assertFalse(CoreLogic.canConsumePotatoHeart(8, 8));
        assertFalse(CoreLogic.canConsumePotatoHeart(9, 8));
    }

    @Test
    void potatoHoeBonusDropsStayWithinConfiguredRange() {
        assertEquals(0, CoreLogic.calculateHoeBonusPotatoes(1, 3, 0.90, 0.75, 0.5));
        assertEquals(1, CoreLogic.calculateHoeBonusPotatoes(1, 3, 0.20, 0.75, 0.0));
        assertEquals(3, CoreLogic.calculateHoeBonusPotatoes(1, 3, 0.20, 0.75, 0.99));
    }
}
