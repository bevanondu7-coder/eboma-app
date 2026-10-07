package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Listing entity representing staycation properties in Kisumu, Kenya.
 * Primary Key: listing_id
 * Foreign Key: host_id -> User(uid)
 *
 * Rule 1: A listing only appears in search results if BOTH is_kyc_verified is true
 * AND is_physical_inspected is true.
 */
@Entity(
    tableName = "listings",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["host_id"]),
        Index(value = ["is_kyc_verified", "is_physical_inspected"]),
        Index(value = ["location"]),
        Index(value = ["price_per_night"])
    ]
)
data class Listing(
    @PrimaryKey
    val listing_id: String,
    val host_id: String,
    val title: String,
    val location: String, // e.g. "Milimani, Kisumu", "Dunga Hill, Kisumu", "Riat Hills, Kisumu"
    val price_per_night: Double,
    val images: List<String>,
    val amenities: List<String>,
    val rating: Double,
    val is_kyc_verified: Boolean,
    val is_physical_inspected: Boolean,
    val description: String = "",
    val max_guests: Int = 8,
    val damage_deposit: Double = 5000.0 // Property damage deposit in KSh
) {
    /**
     * Core Rule 1 helper: Verifies if listing is discoverable by guests.
     */
    fun isGuestSearchVisible(): Boolean = is_kyc_verified && is_physical_inspected
}
