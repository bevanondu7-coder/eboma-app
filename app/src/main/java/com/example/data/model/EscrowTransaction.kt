package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * EscrowTransaction entity holding group funds and damage deposit.
 * Primary Key: escrow_id
 * Foreign Key: booking_id -> Booking(booking_id)
 *
 * Rule 4: Escrow funds release automatically 24 hours after a successful check-in QR scan,
 * unless the guest taps "Freeze Escrow" during that 24-hour window.
 *
 * Rule 5: The property damage deposit is tracked separately from the main booking amount
 * and only releases after both guest and host confirm a clean checkout.
 */
@Entity(
    tableName = "escrow_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Booking::class,
            parentColumns = ["booking_id"],
            childColumns = ["booking_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["booking_id"]),
        Index(value = ["status"]),
        Index(value = ["damage_deposit_status"]),
        Index(value = ["is_frozen_by_guest"]),
        Index(value = ["release_date"])
    ]
)
data class EscrowTransaction(
    @PrimaryKey
    val escrow_id: String,
    val booking_id: String,
    val amount_locked: Double, // Main stay amount held in KSh
    val status: String, // "locked", "released", "frozen", "disputed"
    val release_date: Long, // Epoch timestamp for auto-release (24h after check-in QR scan)
    val damage_deposit_amount: Double, // Tracked separately (in KSh)
    // Rule 4: Guest freeze protection
    val is_frozen_by_guest: Boolean = false,
    val frozen_at: Long? = null,
    val freeze_reason: String? = null,
    // Rule 5: Damage deposit distinct lifecycle ("locked", "released", "disputed")
    val damage_deposit_status: String = "locked",
    // Rule 3: Host scheduled payout time
    val host_payout_scheduled_at: Long = 0L
)
