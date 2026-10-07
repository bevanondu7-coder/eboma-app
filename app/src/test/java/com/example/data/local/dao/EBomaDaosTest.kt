package com.example.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EBomaDatabase
import com.example.data.model.Booking
import com.example.data.model.EscrowTransaction
import com.example.data.model.GroupMember
import com.example.data.model.HostVerification
import com.example.data.model.Listing
import com.example.data.model.Payment
import com.example.data.model.Review
import com.example.data.model.SOSAlert
import com.example.data.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EBomaDaosTest {

    private lateinit var db: EBomaDatabase
    private lateinit var userDao: UserDao
    private lateinit var listingDao: ListingDao
    private lateinit var bookingDao: BookingDao
    private lateinit var groupMemberDao: GroupMemberDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var escrowDao: EscrowDao
    private lateinit var hostVerificationDao: HostVerificationDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var sosAlertDao: SOSAlertDao

    private val defaultHost = User(
        uid = "h1",
        name = "Host Otieno",
        phone = "+254711000000",
        email = "host@eboma.ke",
        role = "host",
        kyc_status = "verified",
        profile_photo = "host.jpg"
    )

    private val defaultGuest = User(
        uid = "g1",
        name = "Guest Bevan",
        phone = "+254722000000",
        email = "guest@eboma.ke",
        role = "guest",
        kyc_status = "verified",
        profile_photo = "guest.jpg"
    )

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EBomaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        listingDao = db.listingDao()
        bookingDao = db.bookingDao()
        groupMemberDao = db.groupMemberDao()
        paymentDao = db.paymentDao()
        escrowDao = db.escrowDao()
        hostVerificationDao = db.hostVerificationDao()
        reviewDao = db.reviewDao()
        sosAlertDao = db.sosAlertDao()

        // Seed default parent users for foreign key references
        userDao.insertUsers(listOf(defaultHost, defaultGuest))
    }

    @After
    fun closeDb() {
        db.close()
    }

    // =========================================================================
    // Foreign Key Referential Integrity Tests
    // =========================================================================

    @Test
    fun foreignKey_cascadeDeleteRemovesChildEntities() = runBlocking {
        // Parent Listing -> Parent Booking -> Child Escrow & Payment & Review
        val listing = Listing(
            listing_id = "lst_cascade_test",
            host_id = defaultHost.uid,
            title = "Cascade Villa",
            location = "Riat, Kisumu",
            price_per_night = 20000.0,
            images = listOf("v1.jpg"),
            amenities = listOf("Pool"),
            rating = 5.0,
            is_kyc_verified = true,
            is_physical_inspected = true
        )
        listingDao.insertListing(listing)

        val booking = Booking(
            booking_id = "bkg_cascade_test",
            listing_id = listing.listing_id,
            group_id = "grp_test",
            check_in_date = 1000L,
            check_out_date = 2000L,
            guest_count = 4,
            total_amount = 20000.0,
            status = "confirmed"
        )
        bookingDao.insertBooking(booking)

        val escrow = EscrowTransaction(
            escrow_id = "esc_cascade_test",
            booking_id = booking.booking_id,
            amount_locked = 20000.0,
            status = "locked",
            release_date = 3000L,
            damage_deposit_amount = 5000.0
        )
        escrowDao.insertEscrow(escrow)

        val payment = Payment(
            payment_id = "pay_cascade_test",
            booking_id = booking.booking_id,
            user_id = defaultGuest.uid,
            amount = 20000.0,
            status = "paid",
            stk_push_ref = "ws_123"
        )
        paymentDao.insertPayment(payment)

        val member = GroupMember(
            group_id = "grp_test",
            user_id = defaultGuest.uid,
            split_amount = 20000.0,
            payment_status = "paid",
            role = "host"
        )
        groupMemberDao.insertMember(member)

        val review = Review(
            review_id = "rev_test",
            booking_id = booking.booking_id,
            user_id = defaultGuest.uid,
            rating = 5.0,
            tags = listOf("Clean"),
            comment = "Great stay"
        )
        reviewDao.insertReview(review)

        val sos = SOSAlert(
            alert_id = "sos_test",
            user_id = defaultGuest.uid,
            location = "Kisumu",
            status = "active"
        )
        sosAlertDao.insertAlert(sos)

        val hostKyc = HostVerification(
            host_id = defaultHost.uid,
            id_document_url = "id.jpg",
            selfie_url = "selfie.mp4",
            kra_pin = "A012345678B",
            kplc_meter_number = "123456",
            gps_lat = -0.1,
            gps_lng = 34.7,
            field_audit_status = "passed",
            estate_signoff_status = "approved"
        )
        hostVerificationDao.insertVerification(hostKyc)

        // Verify all entities inserted successfully
        assertNotNull(listingDao.getListingById("lst_cascade_test"))
        assertNotNull(bookingDao.getBookingById("bkg_cascade_test"))
        assertNotNull(escrowDao.getEscrowById("esc_cascade_test"))
        assertNotNull(paymentDao.getPaymentById("pay_cascade_test"))
        assertNotNull(groupMemberDao.getMember("grp_test", defaultGuest.uid))
        assertNotNull(hostVerificationDao.getVerificationByHost(defaultHost.uid))

        // Deleting listing cascades to booking, which cascades to escrow, payment, review
        listingDao.deleteListingById("lst_cascade_test")

        assertNull(listingDao.getListingById("lst_cascade_test"))
        assertNull(bookingDao.getBookingById("bkg_cascade_test"))
        assertNull(escrowDao.getEscrowById("esc_cascade_test"))
        assertNull(paymentDao.getPaymentById("pay_cascade_test"))
    }

    // =========================================================================
    // ListingDao Tests: Verification and Inspection Filtering
    // =========================================================================

    @Test
    fun listingDao_filtersOnlyBothKycVerifiedAndPhysicalInspected() = runBlocking {
        val fullyVerified = Listing(
            listing_id = "l_pass",
            host_id = defaultHost.uid,
            title = "Dunga Sunset Villa",
            location = "Dunga Beach, Kisumu",
            price_per_night = 15000.0,
            images = listOf("img1.jpg"),
            amenities = listOf("Lake View"),
            rating = 4.9,
            is_kyc_verified = true,
            is_physical_inspected = true,
            max_guests = 8
        )
        val kycOnly = fullyVerified.copy(
            listing_id = "l_kyc_only",
            title = "Milimani Palms",
            is_kyc_verified = true,
            is_physical_inspected = false
        )
        val inspectionOnly = fullyVerified.copy(
            listing_id = "l_inspect_only",
            title = "Riat Hideout",
            is_kyc_verified = false,
            is_physical_inspected = true
        )
        val unverified = fullyVerified.copy(
            listing_id = "l_none",
            title = "Kanyakwar Spot",
            is_kyc_verified = false,
            is_physical_inspected = false
        )

        listingDao.insertListings(listOf(fullyVerified, kycOnly, inspectionOnly, unverified))

        // Guest verified query must ONLY return l_pass
        val guestListings = listingDao.getVerifiedGuestListings()
        assertEquals(1, guestListings.size)
        assertEquals("l_pass", guestListings.first().listing_id)

        // Flow query also emits only verified listings
        val flowListings = listingDao.observeVerifiedGuestListings().first()
        assertEquals(1, flowListings.size)
        assertEquals("l_pass", flowListings.first().listing_id)

        // Direct verified by ID
        assertNotNull(listingDao.getVerifiedListingById("l_pass"))
        assertNull(listingDao.getVerifiedListingById("l_kyc_only"))
        assertNull(listingDao.getVerifiedListingById("l_inspect_only"))

        // Audit queue returns all 3 unverified / pending inspection listings
        val pendingAudit = listingDao.getPendingVerificationAuditListings()
        assertEquals(3, pendingAudit.size)

        // Count queries
        assertEquals(1, listingDao.countVerifiedGuestListings())
        assertEquals(4, listingDao.countTotalListings())
    }

    @Test
    fun listingDao_searchAndFilterQueriesEnforceVerificationGate() = runBlocking {
        val visibleListing = Listing(
            listing_id = "l_dunga",
            host_id = defaultHost.uid,
            title = "Dunga Lakeshore Cottage",
            location = "Dunga Beach, Kisumu",
            price_per_night = 12000.0,
            images = listOf("img1.jpg"),
            amenities = listOf("Pool"),
            rating = 4.8,
            is_kyc_verified = true,
            is_physical_inspected = true,
            max_guests = 6
        )
        val hiddenMatch = visibleListing.copy(
            listing_id = "l_hidden",
            title = "Dunga Secret Haven", // Matches search query "Dunga"
            is_kyc_verified = true,
            is_physical_inspected = false // BUT uninspected! Must stay completely hidden
        )

        listingDao.insertListings(listOf(visibleListing, hiddenMatch))

        // Search for "Dunga"
        val searchResults = listingDao.searchVerifiedGuestListings("Dunga").first()
        assertEquals(1, searchResults.size)
        assertEquals("l_dunga", searchResults.first().listing_id)

        // Filter by location & price
        val filtered = listingDao.filterVerifiedGuestListings(
            location = "Dunga",
            minPrice = 10000.0,
            maxPrice = 15000.0,
            minGuests = 4
        ).first()
        assertEquals(1, filtered.size)
        assertEquals("l_dunga", filtered.first().listing_id)
    }

    // =========================================================================
    // EscrowDao Tests: Managing Escrow States & Auto-Release
    // =========================================================================

    @Test
    fun escrowDao_managesEscrowStateLifecycleAndGuestFreeze() = runBlocking {
        val now = 1000000L
        val listing = Listing(
            listing_id = "l_esc_parent",
            host_id = defaultHost.uid,
            title = "Escrow Parent Stay",
            location = "Milimani, Kisumu",
            price_per_night = 45000.0,
            images = emptyList(),
            amenities = emptyList(),
            rating = 5.0,
            is_kyc_verified = true,
            is_physical_inspected = true
        )
        listingDao.insertListing(listing)

        val booking = Booking(
            booking_id = "bkg_001",
            listing_id = listing.listing_id,
            group_id = "grp_001",
            check_in_date = now,
            check_out_date = now + 86400000L * 2,
            guest_count = 4,
            total_amount = 45000.0,
            status = "confirmed"
        )
        bookingDao.insertBooking(booking)

        val escrow = EscrowTransaction(
            escrow_id = "esc_001",
            booking_id = booking.booking_id,
            amount_locked = 45000.0,
            status = "locked",
            release_date = now + 86400000L, // 24h from now
            damage_deposit_amount = 5000.0,
            damage_deposit_status = "locked",
            is_frozen_by_guest = false
        )

        escrowDao.insertEscrow(escrow)

        // Retrieve by booking ID
        val retrieved = escrowDao.getEscrowByBooking("bkg_001")
        assertNotNull(retrieved)
        assertEquals("locked", retrieved?.status)
        assertEquals(45000.0, retrieved?.amount_locked ?: 0.0, 0.001)

        // Not yet eligible for auto-release because release_date is in the future
        val eligibleBefore = escrowDao.getEscrowsEligibleForAutoRelease(now)
        assertTrue(eligibleBefore.isEmpty())

        // Guest taps "Freeze Escrow"
        escrowDao.freezeEscrow("esc_001", now + 1000L, "Listing amenities did not match description")
        val frozen = escrowDao.getEscrowById("esc_001")
        assertEquals("frozen", frozen?.status)
        assertTrue(frozen?.is_frozen_by_guest == true)
        assertEquals("Listing amenities did not match description", frozen?.freeze_reason)

        // Even if release_date has passed, frozen escrow MUST NOT be eligible for auto-release
        val eligibleAfterTime = escrowDao.getEscrowsEligibleForAutoRelease(now + 90000000L)
        assertTrue("Frozen escrow must never be returned for auto-release", eligibleAfterTime.isEmpty())

        // Frozen escrow appears in observeFrozenOrDisputedEscrows()
        val frozenList = escrowDao.observeFrozenOrDisputedEscrows().first()
        assertEquals(1, frozenList.size)
        assertEquals("esc_001", frozenList.first().escrow_id)

        // Manual resolution: Release escrow
        escrowDao.releaseEscrowFunds("esc_001", now + 95000000L)
        val released = escrowDao.getEscrowById("esc_001")
        assertEquals("released", released?.status)
    }

    @Test
    fun escrowDao_managesSeparateDamageDepositLifecycleAndAggregates() = runBlocking {
        val listing = Listing(
            listing_id = "l_deposit_parent",
            host_id = defaultHost.uid,
            title = "Deposit Parent Stay",
            location = "Dunga, Kisumu",
            price_per_night = 30000.0,
            images = emptyList(),
            amenities = emptyList(),
            rating = 5.0,
            is_kyc_verified = true,
            is_physical_inspected = true
        )
        listingDao.insertListing(listing)

        val booking1 = Booking(
            booking_id = "bkg_1",
            listing_id = listing.listing_id,
            group_id = "grp_1",
            check_in_date = 1000L,
            check_out_date = 2000L,
            guest_count = 2,
            total_amount = 30000.0,
            status = "confirmed"
        )
        val booking2 = Booking(
            booking_id = "bkg_2",
            listing_id = listing.listing_id,
            group_id = "grp_2",
            check_in_date = 3000L,
            check_out_date = 4000L,
            guest_count = 4,
            total_amount = 50000.0,
            status = "confirmed"
        )
        bookingDao.insertBookings(listOf(booking1, booking2))

        val escrow1 = EscrowTransaction(
            escrow_id = "esc_1",
            booking_id = booking1.booking_id,
            amount_locked = 30000.0,
            status = "locked",
            release_date = 2000000L,
            damage_deposit_amount = 4000.0,
            damage_deposit_status = "locked"
        )
        val escrow2 = EscrowTransaction(
            escrow_id = "esc_2",
            booking_id = booking2.booking_id,
            amount_locked = 50000.0,
            status = "locked",
            release_date = 2000000L,
            damage_deposit_amount = 6000.0,
            damage_deposit_status = "locked"
        )

        escrowDao.insertEscrows(listOf(escrow1, escrow2))

        // Check aggregate sums
        val totalLocked = escrowDao.getTotalKShLockedInEscrow()
        assertEquals(80000.0, totalLocked, 0.001)

        val totalDeposits = escrowDao.getTotalDamageDepositsLocked()
        assertEquals(10000.0, totalDeposits, 0.001)

        // Release damage deposit for escrow 1 after clean checkout
        escrowDao.releaseDamageDeposit("esc_1")
        val esc1Updated = escrowDao.getEscrowById("esc_1")
        assertEquals("released", esc1Updated?.damage_deposit_status)

        // Dispute damage deposit for escrow 2
        escrowDao.disputeDamageDeposit("esc_2")
        val esc2Updated = escrowDao.getEscrowById("esc_2")
        assertEquals("disputed", esc2Updated?.damage_deposit_status)

        // Deposits remaining locked is now 0.0
        val remainingLockedDeposits = escrowDao.getTotalDamageDepositsLocked()
        assertEquals(0.0, remainingLockedDeposits, 0.001)
    }
}
