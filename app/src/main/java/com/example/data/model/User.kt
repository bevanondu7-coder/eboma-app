package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User entity representing guests and hosts on e-Boma.
 * Includes KYC verification status and payout phone tracking for escrow/payout fraud protection.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["role"]),
        Index(value = ["kyc_status"]),
        Index(value = ["phone"])
    ]
)
data class User(
    @PrimaryKey
    val uid: String,
    val name: String,
    val phone: String,
    val email: String,
    val role: String, // "guest" or "host"
    val kyc_status: String, // "unverified", "pending", "verified", "flagged_reverification"
    val profile_photo: String,
    // Supporting fields for Rule 3: payout M-Pesa fraud detection
    val payout_mpesa_phone: String? = null,
    val payout_phone_updated_at: Long = 0L,
    val is_payout_frozen: Boolean = false,
    val payout_flag_reason: String? = null
)
