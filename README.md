# PotatoSMP

A complete, modular **PaperMC 1.21.8** (Java 21) survival-multiplayer plugin where potatoes drive the server economy, custom equipment progression, PvP bounties, secure trading, and community milestones.

---

## Features

### 1. Potato Economy & Potato Bill Banking
- **Physical-to-Digital Conversion**: Players harvest potatoes (and rare Golden Potatoes), bring them to **Potato Bill** (zero-dependency persistent Villager NPC), and confirm deposits through a live-verified GUI.
- **Persistent SQLite Ledger**: Balances, lifetime deposited potatoes, Golden Potatoes, Potato Heart upgrades, and timestamped transaction histories are stored in SQLite (`plugins/PotatoSMP/potatosmp.db`) keyed by player UUID.
- **Dynamic Market Fluctuation**: Configurable potato and Golden Potato exchange rates with optional gentle market shifts and trend indicators.

### 2. Potato Shop & Custom Equipment (`/shop`)
All custom items are protected by `PersistentDataContainer` tags and anvil/grindstone/crafting/smithing integrity guards so abilities cannot be stripped by renaming or repairing:
- **Potato Armor Set** (Helmet, Chestplate, Leggings, Boots): Diamond-equivalent armor & toughness stats with custom golden-spud styling; wearing all 4 pieces grants permanent Speed.
- **Spud Striker Sword**: Faster attack cooldown for aggressive PvP combos and Speed boost while held.
- **Crusher Potato Cleaver (Axe)**: High burst damage, slower swing speed, and extended shield disable duration (9s default).
- **Baked Quarry Pickaxe**: Auto-smelts mined ores while preserving Fortune-multiplied drops and awarding smelting XP.
- **Harvest Master Hoe**: Configurable bonus potato drops on mature crops, boosted Golden Potato drop chance, and automatic crop replanting.
- **Potato Heart**: Consumable vitality relic permanently adding +0.5 hearts (+1.0 HP) up to a configurable limit (default: 8).
- **Bounty Hat – The Spud Seeker**: Integrates with **Minecraft 1.21.8's native Player Locator Bar** (`GameRule.LOCATOR_BAR` + `Attribute.WAYPOINT_RECEIVE_RANGE`) to reveal players exclusively within a 50-block radius while equipped in the helmet slot.

### 3. Bounty System (`/bounty`)
- Place or stack credits onto a player's bounty (online or offline).
- Target notifications, multi-contributor tracking, active leaderboard, placed bounties view, and completed bounty history.
- Anti-farming protection (per-pair kill cooldown + daily pair claim cap), self-bounty/contributor-claim prevention, and automatic credit refunds on expiration.

### 4. Netherite Ban & Restrictions
- Removes all Netherite equipment crafting & smithing upgrade recipes and hides them from players' recipe books.
- Configurable policy (`BLOCK_USE` default, `CONFISCATE`, `CONVERT`, or `RECIPES_ONLY`) blocking equipping and usage of existing Netherite gear with potato-themed guidance.

### 5. Additional Systems
- **Golden Potatoes**: Rare crop harvest drop (and optional crafting recipe) exchangeable for high-value Credits or Golden Baked Potatoes.
- **Potato Leaderboards (`/potatotop`)**: Most potatoes deposited, richest players, highest bounties claimed, most bounty rewards earned, and weekly competition standings.
- **Weekly Competitions**: Automated 7-day potato deposit tournament with configurable credit and Golden Potato payouts.
- **Community Potato Monument**: Spawn monument & floating `TextDisplay` hologram tracking total server potato deposits and awarding server-wide milestone rewards + Haste buffs.
- **Secure Player Trading (`/trade <player>`)**: Dual-sided GUI with item + credit offers, lock-in countdown, live balance/space validation, and automatic rollback on close.
- **Sidebar Scoreboard (`/potatoscoreboard`)**: Toggleable HUD displaying credits, deposited potatoes, market rate, active bounty, weekly rank, and monument progress.

---

## Installation

1. Ensure your server runs **PaperMC 1.21.8** (or compatible Paper 1.21.6+ fork) on **Java 21**.
2. Build the plugin JAR or copy the compiled `target/PotatoSMP-1.0.0.jar` into your server's `plugins/` folder.
3. Start the server once to generate `plugins/PotatoSMP/config.yml`, `shop.yml`, `messages.yml`, and `potatosmp.db`.
4. Stand at your spawn bank and run:
   - `/potatoadmin setbills` to spawn **Potato Bill** NPC
   - `/potatoadmin setbank` to set bank coordinates
   - `/potatoadmin monument` to place the **Community Potato Monument** hologram

---

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/shop [category]` | Open the Potato Shop GUI | `potatosmp.shop` |
| `/bounty [place <player> <amt> \| list]` | Open the Bounty Board or place bounties | `potatosmp.bounty` |
| `/bank` | Open the Potato Bank interface | `potatosmp.bank` |
| `/potatobalance [player]` | View credit balance and lifetime potatoes | `potatosmp.balance` (`potatosmp.balance.others`) |
| `/potatotop [category]` | View economy, bounty & weekly leaderboards | `potatosmp.top` |
| `/trade <player \| accept \| decline>` | Securely trade items and credits | `potatosmp.trade` |
| `/potatoscoreboard` | Toggle the PotatoSMP sidebar scoreboard | `potatosmp.scoreboard` |
| `/potatoadmin ...` | Admin commands (`give`, `credits`, `reload`, `setbank`, `setbills`, `reset`, `monument`, `competition`) | `potatosmp.admin` |

- `potatosmp.bypass.netherite` allows bypassing Netherite equipment restrictions (default: `false`).

---

## Building from Source

```bash
mvn clean package
```

The shaded, server-ready plugin JAR is output to `target/PotatoSMP-1.0.0.jar`.
