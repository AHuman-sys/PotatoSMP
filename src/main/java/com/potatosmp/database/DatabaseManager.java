package com.potatosmp.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class DatabaseManager {

    private final File dbFile;
    private final ReentrantLock dbLock = new ReentrantLock(true);
    private Connection connection;

    public DatabaseManager(File dbFile) {
        this.dbFile = dbFile;
    }

    public void init() throws SQLException {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA busy_timeout=5000;");
        }
        createSchema();
    }

    private void createSchema() throws SQLException {
        dbLock.lock();
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        credits INTEGER NOT NULL DEFAULT 0,
                        lifetime_potatoes INTEGER NOT NULL DEFAULT 0,
                        lifetime_golden_potatoes INTEGER NOT NULL DEFAULT 0,
                        potato_hearts_used INTEGER NOT NULL DEFAULT 0,
                        bounties_claimed_count INTEGER NOT NULL DEFAULT 0,
                        bounty_credits_earned INTEGER NOT NULL DEFAULT 0,
                        scoreboard_enabled INTEGER NOT NULL DEFAULT 1,
                        updated_at INTEGER NOT NULL
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        type TEXT NOT NULL,
                        credit_delta INTEGER NOT NULL,
                        potato_count INTEGER NOT NULL DEFAULT 0,
                        details TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    );
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_player_time ON transactions(player_uuid, created_at DESC);");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS bounties (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        target_uuid TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        total_amount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        claimed_by_uuid TEXT,
                        claimed_by_name TEXT,
                        claimed_at INTEGER
                    );
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_bounties_status_target ON bounties(status, target_uuid);");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS bounty_contributions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        bounty_id INTEGER NOT NULL,
                        contributor_uuid TEXT NOT NULL,
                        contributor_name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        contributed_at INTEGER NOT NULL,
                        FOREIGN KEY(bounty_id) REFERENCES bounties(id) ON DELETE CASCADE
                    );
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_bounty_contrib_id ON bounty_contributions(bounty_id);");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS bounty_pair_claims (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        killer_uuid TEXT NOT NULL,
                        victim_uuid TEXT NOT NULL,
                        claimed_at INTEGER NOT NULL
                    );
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_pair_claims ON bounty_pair_claims(killer_uuid, victim_uuid, claimed_at DESC);");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS weekly_competitions (
                        week_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at INTEGER NOT NULL,
                        ends_at INTEGER NOT NULL,
                        finalized INTEGER NOT NULL DEFAULT 0
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS weekly_entries (
                        week_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        potatoes_deposited INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(week_id, player_uuid),
                        FOREIGN KEY(week_id) REFERENCES weekly_competitions(week_id) ON DELETE CASCADE
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS monument_state (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        total_potatoes INTEGER NOT NULL DEFAULT 0
                    );
                    """);
            st.execute("INSERT OR IGNORE INTO monument_state (id, total_potatoes) VALUES (1, 0);");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS monument_unlocked (
                        milestone_key TEXT PRIMARY KEY,
                        unlocked_at INTEGER NOT NULL
                    );
                    """);
        } finally {
            dbLock.unlock();
        }
    }

    public void close() {
        dbLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            dbLock.unlock();
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T executeLocked(SqlWork<T> work) throws SQLException {
        dbLock.lock();
        try {
            return work.execute(connection);
        } finally {
            dbLock.unlock();
        }
    }

    public <T> T executeInTransaction(SqlWork<T> work) throws SQLException {
        dbLock.lock();
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Throwable ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                if (ex instanceof SQLException sqlEx) {
                    throw sqlEx;
                }
                throw new SQLException("Transaction aborted", ex);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {}
            }
        } finally {
            dbLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Player Profiles
    // -------------------------------------------------------------------------

    public PlayerProfile loadOrCreatePlayer(UUID uuid, String knownName, long defaultCredits) throws SQLException {
        return executeInTransaction(conn -> {
            Optional<PlayerProfile> existing = findPlayerInternal(conn, uuid);
            if (existing.isPresent()) {
                PlayerProfile profile = existing.get();
                if (knownName != null && !knownName.equals(profile.getLastKnownName())) {
                    profile.setLastKnownName(knownName);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE players SET last_known_name = ?, updated_at = ? WHERE uuid = ?")) {
                        ps.setString(1, knownName);
                        ps.setLong(2, System.currentTimeMillis());
                        ps.setString(3, uuid.toString());
                        ps.executeUpdate();
                    }
                }
                return profile;
            }
            long now = System.currentTimeMillis();
            String safeName = knownName != null ? knownName : uuid.toString().substring(0, 8);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO players (uuid, last_known_name, credits, lifetime_potatoes,
                        lifetime_golden_potatoes, potato_hearts_used, bounties_claimed_count,
                        bounty_credits_earned, scoreboard_enabled, updated_at)
                    VALUES (?, ?, ?, 0, 0, 0, 0, 0, 1, ?)
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, safeName);
                ps.setLong(3, Math.max(0L, defaultCredits));
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return new PlayerProfile(uuid, safeName, Math.max(0L, defaultCredits), 0L, 0L, 0, 0, 0L, true);
        });
    }

    public Optional<PlayerProfile> findPlayer(UUID uuid) throws SQLException {
        return executeLocked(conn -> findPlayerInternal(conn, uuid));
    }

    public Optional<PlayerProfile> findPlayerByName(String name) throws SQLException {
        return executeLocked(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM players WHERE LOWER(last_known_name) = LOWER(?) LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPlayer(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    private Optional<PlayerProfile> findPlayerInternal(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPlayer(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void savePlayer(PlayerProfile profile) throws SQLException {
        executeLocked(conn -> {
            savePlayerInternal(conn, profile);
            return null;
        });
    }

    public void savePlayerInternal(Connection conn, PlayerProfile profile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE players
                SET last_known_name = ?,
                    credits = ?,
                    lifetime_potatoes = ?,
                    lifetime_golden_potatoes = ?,
                    potato_hearts_used = ?,
                    bounties_claimed_count = ?,
                    bounty_credits_earned = ?,
                    scoreboard_enabled = ?,
                    updated_at = ?
                WHERE uuid = ?
                """)) {
            ps.setString(1, profile.getLastKnownName());
            ps.setLong(2, profile.getCredits());
            ps.setLong(3, profile.getLifetimePotatoes());
            ps.setLong(4, profile.getLifetimeGoldenPotatoes());
            ps.setInt(5, profile.getPotatoHeartsUsed());
            ps.setInt(6, profile.getBountiesClaimedCount());
            ps.setLong(7, profile.getBountyCreditsEarned());
            ps.setInt(8, profile.isScoreboardEnabled() ? 1 : 0);
            ps.setLong(9, System.currentTimeMillis());
            ps.setString(10, profile.getUuid().toString());
            ps.executeUpdate();
        }
    }

    private PlayerProfile mapPlayer(ResultSet rs) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("last_known_name"),
                rs.getLong("credits"),
                rs.getLong("lifetime_potatoes"),
                rs.getLong("lifetime_golden_potatoes"),
                rs.getInt("potato_hearts_used"),
                rs.getInt("bounties_claimed_count"),
                rs.getLong("bounty_credits_earned"),
                rs.getInt("scoreboard_enabled") == 1
        );
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    public void insertTransactionInternal(Connection conn, UUID playerUuid, TransactionType type,
                                          long creditDelta, int potatoCount, String details) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO transactions (player_uuid, type, credit_delta, potato_count, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            ps.setLong(3, creditDelta);
            ps.setInt(4, potatoCount);
            ps.setString(5, details);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<TransactionRecord> getTransactions(UUID playerUuid, int limit) throws SQLException {
        return executeLocked(conn -> {
            List<TransactionRecord> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT * FROM transactions
                    WHERE player_uuid = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                    """)) {
                ps.setString(1, playerUuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new TransactionRecord(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                TransactionType.valueOf(rs.getString("type")),
                                rs.getLong("credit_delta"),
                                rs.getInt("potato_count"),
                                rs.getString("details"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            }
            return list;
        });
    }

    // -------------------------------------------------------------------------
    // Leaderboards
    // -------------------------------------------------------------------------

    public List<PlayerProfile> getTopPlayers(String orderByColumn, int limit) throws SQLException {
        String safeCol = switch (orderByColumn) {
            case "lifetime_potatoes", "credits", "bounties_claimed_count", "bounty_credits_earned" -> orderByColumn;
            default -> "credits";
        };
        return executeLocked(conn -> {
            List<PlayerProfile> result = new ArrayList<>();
            String sql = "SELECT * FROM players ORDER BY " + safeCol + " DESC, updated_at ASC LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapPlayer(rs));
                    }
                }
            }
            return result;
        });
    }

    // -------------------------------------------------------------------------
    // Bounties
    // -------------------------------------------------------------------------

    public Optional<BountyRecord> getActiveBountyByTarget(UUID targetUuid) throws SQLException {
        return executeLocked(conn -> getActiveBountyByTargetInternal(conn, targetUuid));
    }

    public Optional<BountyRecord> getActiveBountyByTargetInternal(Connection conn, UUID targetUuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM bounties WHERE status = 'ACTIVE' AND target_uuid = ? LIMIT 1")) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBounty(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<BountyRecord> getActiveBounties(int limit) throws SQLException {
        return executeLocked(conn -> {
            List<BountyRecord> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT * FROM bounties
                    WHERE status = 'ACTIVE'
                    ORDER BY total_amount DESC, created_at ASC
                    LIMIT ?
                    """)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapBounty(rs));
                    }
                }
            }
            return list;
        });
    }

    public List<BountyRecord> getCompletedBounties(int limit) throws SQLException {
        return executeLocked(conn -> {
            List<BountyRecord> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT * FROM bounties
                    WHERE status = 'CLAIMED'
                    ORDER BY claimed_at DESC
                    LIMIT ?
                    """)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapBounty(rs));
                    }
                }
            }
            return list;
        });
    }

    public List<BountyRecord> getBountiesPlacedBy(UUID contributorUuid, int limit) throws SQLException {
        return executeLocked(conn -> {
            List<BountyRecord> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT DISTINCT b.*
                    FROM bounties b
                    INNER JOIN bounty_contributions c ON b.id = c.bounty_id
                    WHERE c.contributor_uuid = ?
                    ORDER BY b.created_at DESC
                    LIMIT ?
                    """)) {
                ps.setString(1, contributorUuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapBounty(rs));
                    }
                }
            }
            return list;
        });
    }

    public List<BountyContribution> getBountyContributions(long bountyId) throws SQLException {
        return executeLocked(conn -> getBountyContributionsInternal(conn, bountyId));
    }

    public List<BountyContribution> getBountyContributionsInternal(Connection conn, long bountyId) throws SQLException {
        List<BountyContribution> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM bounty_contributions WHERE bounty_id = ? ORDER BY contributed_at ASC")) {
            ps.setLong(1, bountyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BountyContribution(
                            rs.getLong("id"),
                            rs.getLong("bounty_id"),
                            UUID.fromString(rs.getString("contributor_uuid")),
                            rs.getString("contributor_name"),
                            rs.getLong("amount"),
                            rs.getLong("contributed_at")
                    ));
                }
            }
        }
        return list;
    }

    public long createBountyInternal(Connection conn, UUID targetUuid, String targetName,
                                     long amount, long createdAt, long expiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO bounties (target_uuid, target_name, total_amount, status, created_at, expires_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, targetUuid.toString());
            ps.setString(2, targetName);
            ps.setLong(3, amount);
            ps.setLong(4, createdAt);
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated bounty ID");
    }

    public void addBountyAmountInternal(Connection conn, long bountyId, long addAmount, long newExpiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE bounties
                SET total_amount = total_amount + ?,
                    expires_at = MAX(expires_at, ?)
                WHERE id = ?
                """)) {
            ps.setLong(1, addAmount);
            ps.setLong(2, newExpiresAt);
            ps.setLong(3, bountyId);
            ps.executeUpdate();
        }
    }

    public void addBountyContributionInternal(Connection conn, long bountyId, UUID contributorUuid,
                                              String contributorName, long amount, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO bounty_contributions (bounty_id, contributor_uuid, contributor_name, amount, contributed_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, bountyId);
            ps.setString(2, contributorUuid.toString());
            ps.setString(3, contributorName);
            ps.setLong(4, amount);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public void markBountyClaimedInternal(Connection conn, long bountyId, UUID killerUuid,
                                          String killerName, long claimedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE bounties
                SET status = 'CLAIMED', claimed_by_uuid = ?, claimed_by_name = ?, claimed_at = ?
                WHERE id = ?
                """)) {
            ps.setString(1, killerUuid.toString());
            ps.setString(2, killerName);
            ps.setLong(3, claimedAt);
            ps.setLong(4, bountyId);
            ps.executeUpdate();
        }
    }

    public void markBountyStatusInternal(Connection conn, long bountyId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE bounties SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, bountyId);
            ps.executeUpdate();
        }
    }

    public List<BountyRecord> getExpiredActiveBountiesInternal(Connection conn, long now) throws SQLException {
        List<BountyRecord> expired = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM bounties WHERE status = 'ACTIVE' AND expires_at <= ?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    expired.add(mapBounty(rs));
                }
            }
        }
        return expired;
    }

    public long getLastPairClaimTimeInternal(Connection conn, UUID killerUuid, UUID victimUuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT claimed_at FROM bounty_pair_claims
                WHERE killer_uuid = ? AND victim_uuid = ?
                ORDER BY claimed_at DESC LIMIT 1
                """)) {
            ps.setString(1, killerUuid.toString());
            ps.setString(2, victimUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("claimed_at");
                }
            }
        }
        return 0L;
    }

    public int getPairClaimsSinceInternal(Connection conn, UUID killerUuid, UUID victimUuid, long sinceMillis) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM bounty_pair_claims
                WHERE killer_uuid = ? AND victim_uuid = ? AND claimed_at >= ?
                """)) {
            ps.setString(1, killerUuid.toString());
            ps.setString(2, victimUuid.toString());
            ps.setLong(3, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void recordPairClaimInternal(Connection conn, UUID killerUuid, UUID victimUuid, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bounty_pair_claims (killer_uuid, victim_uuid, claimed_at) VALUES (?, ?, ?)")) {
            ps.setString(1, killerUuid.toString());
            ps.setString(2, victimUuid.toString());
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private BountyRecord mapBounty(ResultSet rs) throws SQLException {
        String claimedByStr = rs.getString("claimed_by_uuid");
        long claimedAtRaw = rs.getLong("claimed_at");
        Long claimedAt = rs.wasNull() ? null : claimedAtRaw;
        return new BountyRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                rs.getLong("total_amount"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                claimedByStr == null ? null : UUID.fromString(claimedByStr),
                rs.getString("claimed_by_name"),
                claimedAt
        );
    }

    // -------------------------------------------------------------------------
    // Weekly Competition & Monument
    // -------------------------------------------------------------------------

    public record WeeklyCompetition(long weekId, long startedAt, long endsAt, boolean finalized) {}
    public record WeeklyEntry(long weekId, UUID playerUuid, String playerName, long potatoesDeposited) {}

    public WeeklyCompetition getOrCreateCurrentWeek(long durationMillis) throws SQLException {
        return executeInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM weekly_competitions WHERE finalized = 0 ORDER BY week_id DESC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new WeeklyCompetition(
                                rs.getLong("week_id"),
                                rs.getLong("started_at"),
                                rs.getLong("ends_at"),
                                rs.getInt("finalized") == 1
                        );
                    }
                }
            }
            long now = System.currentTimeMillis();
            long ends = now + durationMillis;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO weekly_competitions (started_at, ends_at, finalized) VALUES (?, ?, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, now);
                ps.setLong(2, ends);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new WeeklyCompetition(keys.getLong(1), now, ends, false);
                    }
                }
            }
            throw new SQLException("Failed to create weekly competition");
        });
    }

    public void addWeeklyDepositInternal(Connection conn, long weekId, UUID playerUuid, String playerName, long potatoes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO weekly_entries (week_id, player_uuid, player_name, potatoes_deposited)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(week_id, player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    potatoes_deposited = potatoes_deposited + excluded.potatoes_deposited
                """)) {
            ps.setLong(1, weekId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setLong(4, potatoes);
            ps.executeUpdate();
        }
    }

    public List<WeeklyEntry> getWeeklyTopEntries(long weekId, int limit) throws SQLException {
        return executeLocked(conn -> {
            List<WeeklyEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT * FROM weekly_entries
                    WHERE week_id = ?
                    ORDER BY potatoes_deposited DESC
                    LIMIT ?
                    """)) {
                ps.setLong(1, weekId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new WeeklyEntry(
                                rs.getLong("week_id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("player_name"),
                                rs.getLong("potatoes_deposited")
                        ));
                    }
                }
            }
            return entries;
        });
    }

    public int getPlayerWeeklyRank(long weekId, UUID playerUuid) throws SQLException {
        return executeLocked(conn -> {
            long playerTotal = -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT potatoes_deposited FROM weekly_entries WHERE week_id = ? AND player_uuid = ?")) {
                ps.setLong(1, weekId);
                ps.setString(2, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        playerTotal = rs.getLong(1);
                    }
                }
            }
            if (playerTotal <= 0) {
                return -1;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) + 1 FROM weekly_entries WHERE week_id = ? AND potatoes_deposited > ?")) {
                ps.setLong(1, weekId);
                ps.setLong(2, playerTotal);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
        });
    }

    public void finalizeWeek(long weekId) throws SQLException {
        executeLocked(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE weekly_competitions SET finalized = 1 WHERE week_id = ?")) {
                ps.setLong(1, weekId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public long getMonumentTotal() throws SQLException {
        return executeLocked(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT total_potatoes FROM monument_state WHERE id = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            return 0L;
        });
    }

    public long addMonumentPotatoesInternal(Connection conn, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE monument_state SET total_potatoes = MAX(0, total_potatoes + ?) WHERE id = 1")) {
            ps.setLong(1, delta);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT total_potatoes FROM monument_state WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    public Set<String> getUnlockedMilestones() throws SQLException {
        return executeLocked(conn -> {
            Set<String> set = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT milestone_key FROM monument_unlocked")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        set.add(rs.getString(1));
                    }
                }
            }
            return set;
        });
    }

    public void markMilestoneUnlocked(String milestoneKey) throws SQLException {
        executeLocked(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO monument_unlocked (milestone_key, unlocked_at) VALUES (?, ?)")) {
                ps.setString(1, milestoneKey);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void resetPlayerData(UUID uuid) throws SQLException {
        executeInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE players
                    SET credits = 0,
                        lifetime_potatoes = 0,
                        lifetime_golden_potatoes = 0,
                        potato_hearts_used = 0,
                        bounties_claimed_count = 0,
                        bounty_credits_earned = 0,
                        updated_at = ?
                    WHERE uuid = ?
                    """)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
