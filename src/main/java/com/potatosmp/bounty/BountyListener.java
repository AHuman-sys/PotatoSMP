package com.potatosmp.bounty;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class BountyListener implements Listener {

    private final BountyService bountyService;

    public BountyListener(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            bountyService.handleKill(killer, victim);
        }
    }
}
