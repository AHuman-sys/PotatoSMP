package com.potatosmp.database;

public enum TransactionType {
    DEPOSIT("Potato Deposit"),
    SHOP_BUY("Shop Purchase"),
    BOUNTY_PLACE("Bounty Placed"),
    BOUNTY_CLAIM("Bounty Claimed"),
    BOUNTY_REFUND("Bounty Refund"),
    TRADE("Player Trade"),
    ADMIN_ADJUST("Admin Adjustment"),
    COMPETITION_REWARD("Weekly Competition"),
    MONUMENT_REWARD("Monument Milestone");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
