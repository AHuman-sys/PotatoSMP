package com.potatosmp;

import com.potatosmp.database.BountyRecord;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.database.TransactionRecord;
import com.potatosmp.database.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlitePersistsProfilesTransactionsBountiesAndMonument() throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("test-potatosmp.db").toFile());
        db.init();
        try {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            PlayerProfile pAlice = db.loadOrCreatePlayer(alice, "Alice", 0L);
            PlayerProfile pBob = db.loadOrCreatePlayer(bob, "Bob", 500L);
            assertEquals(0L, pAlice.getCredits());
            assertEquals(500L, pBob.getCredits());

            long newMonument = db.executeInTransaction(conn -> {
                pAlice.setCredits(1000L);
                pAlice.addLifetimePotatoes(500L);
                db.savePlayerInternal(conn, pAlice);
                db.insertTransactionInternal(conn, alice, TransactionType.DEPOSIT, 1000L, 500, "Deposit 500");
                return db.addMonumentPotatoesInternal(conn, 500L);
            });
            assertEquals(500L, newMonument);
            assertEquals(500L, db.getMonumentTotal());

            List<TransactionRecord> tx = db.getTransactions(alice, 10);
            assertEquals(1, tx.size());
            assertEquals(1000L, tx.getFirst().creditDelta());

            long now = System.currentTimeMillis();
            long bountyId = db.executeInTransaction(conn -> {
                long id = db.createBountyInternal(conn, bob, "Bob", 750L, now, now + 60_000L);
                db.addBountyContributionInternal(conn, id, alice, "Alice", 750L, now);
                return id;
            });
            assertTrue(bountyId > 0);

            Optional<BountyRecord> active = db.getActiveBountyByTarget(bob);
            assertTrue(active.isPresent());
            assertEquals(750L, active.get().totalAmount());
            assertEquals(1, db.getBountyContributions(bountyId).size());

            // Weekly competition deposit
            var week = db.getOrCreateCurrentWeek(7 * 86_400_000L);
            db.executeInTransaction(conn -> {
                db.addWeeklyDepositInternal(conn, week.weekId(), alice, "Alice", 500L);
                return null;
            });
            assertEquals(1, db.getPlayerWeeklyRank(week.weekId(), alice));
        } finally {
            db.close();
        }
    }
}
