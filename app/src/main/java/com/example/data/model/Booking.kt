package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Booking entity representing a group staycation booking.
 * Primary Key: booking_id
 * Foreign Key: listing_id -> Listing(listing_id)
 *
 * Supports QR check-in timestamp (Rule 4) and mutual clean checkout confirmations (Rule 5).
 */
@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(
            entity = Listing::class,
            parentColumns = ["listing_id"],
            childColumns = ["listing_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["listing_id"]),
        Index(value = ["group_id"]),
        Index(value = ["status"]),
        Index(value = ["check_in_date"])
    ]
)
data class Booking(
    @PrimaryKey
    val booking_id: String,
    val listing_id: String,
    val group_id: String,
    val check_in_date: Long,
    val check_out_date: Long,
    val guest_count: Int,
    val total_amount: Double, // In KSh
    val status: String, // "pending_payment", "confirmed", "checked_in", "checked_out", "completed", "cancelled"
    // Supporting fields for Rule 4 (Escrow QR Check-In) & Rule 5 (Clean Checkout)
    val check_in_qr_scanned_at: Long? = null,
    val is_clean_checkout_guest_confirmed: Boolean = false,
    val is_clean_checkout_host_confirmed: Boolean = false
)
