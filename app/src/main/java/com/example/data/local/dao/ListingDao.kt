package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Listing
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Staycation Listings in Kisumu.
 *
 * Implements Rule 1:
 * "A listing only appears in search results if BOTH is_kyc_verified is true
 * AND is_physical_inspected is true. Unverified listings stay completely hidden from guests."
 */
@Dao
interface ListingDao {

    // =========================================================================
    // Rule 1: Guest-Facing Verified & Inspected Queries
    // =========================================================================

    /**
     * Observes all listings eligible for guest search.
     * Enforces BOTH is_kyc_verified = 1 AND is_physical_inspected = 1.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE is_kyc_verified = 1 AND is_physical_inspected = 1 
        ORDER BY rating DESC, price_per_night ASC
    """)
    fun observeVerifiedGuestListings(): Flow<List<Listing>>

    /**
     * Synchronous / suspend query for guest-eligible listings.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE is_kyc_verified = 1 AND is_physical_inspected = 1 
        ORDER BY rating DESC, price_per_night ASC
    """)
    suspend fun getVerifiedGuestListings(): List<Listing>

    /**
     * Search guest-eligible listings by keyword (title, location, or description).
     * Strictly hides any listing not verified and inspected.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE is_kyc_verified = 1 AND is_physical_inspected = 1 
          AND (title LIKE '%' || :query || '%' 
               OR location LIKE '%' || :query || '%' 
               OR description LIKE '%' || :query || '%')
        ORDER BY rating DESC
    """)
    fun searchVerifiedGuestListings(query: String): Flow<List<Listing>>

    /**
     * Filter guest-eligible listings by location, price range, and minimum guest capacity.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE is_kyc_verified = 1 AND is_physical_inspected = 1 
          AND (:location IS NULL OR :location = '' OR location LIKE '%' || :location || '%')
          AND price_per_night BETWEEN :minPrice AND :maxPrice 
          AND max_guests >= :minGuests
        ORDER BY price_per_night ASC
    """)
    fun filterVerifiedGuestListings(
        location: String?,
        minPrice: Double = 0.0,
        maxPrice: Double = 100000.0,
        minGuests: Int = 1
    ): Flow<List<Listing>>

    /**
     * Fetches a single verified & inspected listing for guests. Returns null if hidden.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE listing_id = :listingId AND is_kyc_verified = 1 AND is_physical_inspected = 1
    """)
    suspend fun getVerifiedListingById(listingId: String): Listing?

    // =========================================================================
    // Admin, Host & Inspection Audit Queries
    // =========================================================================

    /**
     * Get any listing by ID (regardless of verification status, for host/admin edit).
     */
    @Query("SELECT * FROM listings WHERE listing_id = :listingId")
    suspend fun getListingById(listingId: String): Listing?

    @Query("SELECT * FROM listings WHERE listing_id = :listingId")
    fun observeListingById(listingId: String): Flow<Listing?>

    /**
     * Administrative query to see all listings regardless of status.
     */
    @Query("SELECT * FROM listings ORDER BY title ASC")
    suspend fun getAllListingsAdmin(): List<Listing>

    /**
     * Host listings management: Host can see their own listings even if unverified.
     */
    @Query("SELECT * FROM listings WHERE host_id = :hostId ORDER BY title ASC")
    suspend fun getListingsByHost(hostId: String): List<Listing>

    @Query("SELECT * FROM listings WHERE host_id = :hostId ORDER BY title ASC")
    fun observeListingsByHost(hostId: String): Flow<List<Listing>>

    /**
     * Unverified / Uninspected audit queue: listings needing field audit or KYC review.
     */
    @Query("""
        SELECT * FROM listings 
        WHERE is_kyc_verified = 0 OR is_physical_inspected = 0
        ORDER BY listing_id DESC
    """)
    suspend fun getPendingVerificationAuditListings(): List<Listing>

    /**
     * Total count of active guest-discoverable listings.
     */
    @Query("SELECT COUNT(*) FROM listings WHERE is_kyc_verified = 1 AND is_physical_inspected = 1")
    suspend fun countVerifiedGuestListings(): Int

    /**
     * Total count of all listings on platform.
     */
    @Query("SELECT COUNT(*) FROM listings")
    suspend fun countTotalListings(): Int

    // =========================================================================
    // Mutations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListing(listing: Listing)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListings(listings: List<Listing>)

    @Update
    suspend fun updateListing(listing: Listing)

    /**
     * Admin KYC sign-off mutation.
     */
    @Query("UPDATE listings SET is_kyc_verified = :isVerified WHERE listing_id = :listingId")
    suspend fun updateListingKycStatus(listingId: String, isVerified: Boolean)

    /**
     * Admin Physical inspection sign-off mutation.
     */
    @Query("UPDATE listings SET is_physical_inspected = :isInspected WHERE listing_id = :listingId")
    suspend fun updateListingPhysicalInspectionStatus(listingId: String, isInspected: Boolean)

    @Query("DELETE FROM listings WHERE listing_id = :listingId")
    suspend fun deleteListingById(listingId: String)
}
