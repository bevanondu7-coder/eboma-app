package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.EscrowTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Escrow Transactions & Damage Deposit Management.
 *
 * Implements:
 * Rule 4: Escrow funds release automatically 24 hours after a successful check-in QR scan,
 *         unless the guest taps "Freeze Escrow" during that 24-hour window.
 * Rule 5: The property damage deposit is tracked separately from the main booking amount
 *         and only releases after both guest and host confirm a clean checkout.
 */
@Dao
interface EscrowDao {

    // =========================================================================
    // Core Queries
    // =========================================================================

    @Query("SELECT * FROM escrow_transactions WHERE escrow_id = :escrowId")
    suspend fun getEscrowById(escrowId: String): EscrowTransaction?

    @Query("SELECT * FROM escrow_transactions WHERE escrow_id = :escrowId")
    fun observeEscrowById(escrowId: String): Flow<EscrowTransaction?>

    @Query("SELECT * FROM escrow_transactions WHERE booking_id = :bookingId")
    suspend fun getEscrowByBooking(bookingId: String): EscrowTransaction?

    @Query("SELECT * FROM escrow_transactions WHERE booking_id = :bookingId")
    fun observeEscrowByBooking(bookingId: String): Flow<EscrowTransaction?>

    // =========================================================================
    // Rule 4: Escrow State Queries & Auto-Release Processing
    // =========================================================================

    /**
     * Filter escrows by primary lifecycle state ("locked", "released", "frozen", "disputed").
     */
    @Query("SELECT * FROM escrow_transactions WHERE status = :status ORDER BY release_date ASC")
    fun observeEscrowsByStatus(status: String): Flow<List<EscrowTransaction>>

    @Query("SELECT * FROM escrow_transactions WHERE status = :status ORDER BY release_date ASC")
    suspend fun getEscrowsByStatus(status: String): List<EscrowTransaction>

    /**
     * Candidates for automatic escrow release:
     * - Status is currently 'locked'
     * - Guest has NOT frozen escrow (is_frozen_by_guest = 0)
     * - 24-hour post-check-in release target date has passed (release_date > 0 AND release_date <= currentTime)
     */
    @Query("""
        SELECT * FROM escrow_transactions 
        WHERE status = 'locked' 
          AND is_frozen_by_guest = 0 
          AND release_date > 0 
          AND release_date <= :currentTime
    """)
    suspend fun getEscrowsEligibleForAutoRelease(currentTime: Long): List<EscrowTransaction>

    /**
     * Observes all escrows frozen by guests or marked as disputed.
     * Crucial for customer care mediation and guest stay safety.
     */
    @Query("""
        SELECT * FROM escrow_transactions 
        WHERE is_frozen_by_guest = 1 OR status = 'frozen' OR status = 'disputed'
        ORDER BY frozen_at DESC
    """)
    fun observeFrozenOrDisputedEscrows(): Flow<List<EscrowTransaction>>

    // =========================================================================
    // Rule 5: Damage Deposit Separate State Tracking
    // =========================================================================

    /**
     * Filter escrows by damage deposit status ("locked", "released", "disputed").
     */
    @Query("""
        SELECT * FROM escrow_transactions 
        WHERE damage_deposit_status = :damageDepositStatus
    """)
    fun observeEscrowsByDamageDepositStatus(damageDepositStatus: String): Flow<List<EscrowTransaction>>

    @Query("""
        SELECT * FROM escrow_transactions 
        WHERE damage_deposit_status = :damageDepositStatus
    """)
    suspend fun getEscrowsByDamageDepositStatus(damageDepositStatus: String): List<EscrowTransaction>

    // =========================================================================
    // Direct Escrow State Transitions (Mutations)
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEscrow(escrow: EscrowTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEscrows(escrows: List<EscrowTransaction>)

    @Update
    suspend fun updateEscrow(escrow: EscrowTransaction)

    /**
     * Rule 4 State Transition: Guest taps "Freeze Escrow" within 24 hours of QR check-in.
     */
    @Query("""
        UPDATE escrow_transactions 
        SET status = 'frozen', 
            is_frozen_by_guest = 1, 
            frozen_at = :frozenAt, 
            freeze_reason = :reason 
        WHERE escrow_id = :escrowId
    """)
    suspend fun freezeEscrow(escrowId: String, frozenAt: Long, reason: String): Int

    /**
     * Rule 4 State Transition: Funds released to host (either automatically or upon dispute resolution).
     */
    @Query("""
        UPDATE escrow_transactions 
        SET status = 'released', 
            release_date = :releasedAt 
        WHERE escrow_id = :escrowId
    """)
    suspend fun releaseEscrowFunds(escrowId: String, releasedAt: Long): Int

    /**
     * Elevates frozen escrow into formal dispute.
     */
    @Query("""
        UPDATE escrow_transactions 
        SET status = 'disputed', 
            freeze_reason = :disputeReason 
        WHERE escrow_id = :escrowId
    """)
    suspend fun markEscrowDisputed(escrowId: String, disputeReason: String): Int

    /**
     * Rule 5 State Transition: Release separate damage deposit back to guest.
     */
    @Query("""
        UPDATE escrow_transactions 
        SET damage_deposit_status = 'released' 
        WHERE escrow_id = :escrowId
    """)
    suspend fun releaseDamageDeposit(escrowId: String): Int

    /**
     * Rule 5 State Transition: Damage claim flagged on deposit.
     */
    @Query("""
        UPDATE escrow_transactions 
        SET damage_deposit_status = 'disputed' 
        WHERE escrow_id = :escrowId
    """)
    suspend fun disputeDamageDeposit(escrowId: String): Int

    /**
     * Updates scheduled payout timestamp for host (Rule 3 integration).
     */
    @Query("""
        UPDATE escrow_transactions 
        SET host_payout_scheduled_at = :payoutTime 
        WHERE escrow_id = :escrowId
    """)
    suspend fun updateScheduledPayoutTime(escrowId: String, payoutTime: Long): Int

    // =========================================================================
    // Financial Aggregate Totals (in KSh)
    // =========================================================================

    /**
     * Total sum of KSh currently locked in primary escrow holding.
     */
    @Query("SELECT COALESCE(SUM(amount_locked), 0.0) FROM escrow_transactions WHERE status = 'locked'")
    suspend fun getTotalKShLockedInEscrow(): Double

    /**
     * Total sum of KSh currently held as damage deposits.
     */
    @Query("SELECT COALESCE(SUM(damage_deposit_amount), 0.0) FROM escrow_transactions WHERE damage_deposit_status = 'locked'")
    suspend fun getTotalDamageDepositsLocked(): Double
}
