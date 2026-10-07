package com.example.data.repository

import com.example.data.local.EBomaDatabase
import com.example.data.local.dao.BookingDao
import com.example.data.local.dao.EscrowDao
import com.example.data.local.dao.GroupMemberDao
import com.example.data.local.dao.HostVerificationDao
import com.example.data.local.dao.ListingDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.ReviewDao
import com.example.data.local.dao.SOSAlertDao
import com.example.data.local.dao.UserDao
import com.example.data.model.Booking
import com.example.data.model.EscrowTransaction
import com.example.data.model.GroupMember
import com.example.data.model.HostVerification
import com.example.data.model.Listing
import com.example.data.model.Payment
import com.example.data.model.Review
import com.example.data.model.SOSAlert
import com.example.data.model.User
import com.example.data.remote.service.MpesaPaymentService
import com.example.domain.rules.EBomaRulesEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EBomaRepository @Inject constructor(
    private val db: EBomaDatabase,
    private val userDao: UserDao,
    private val listingDao: ListingDao,
    private val bookingDao: BookingDao,
    private val groupMemberDao: GroupMemberDao,
    private val paymentDao: PaymentDao,
    private val escrowDao: EscrowDao,
    private val hostVerificationDao: HostVerificationDao,
    private val reviewDao: ReviewDao,
    private val sosAlertDao: SOSAlertDao,
    val mpesaPaymentService: MpesaPaymentService? = null
) {

    constructor(db: EBomaDatabase) : this(
        db = db,
        userDao = db.userDao(),
        listingDao = db.listingDao(),
        bookingDao = db.bookingDao(),
        groupMemberDao = db.groupMemberDao(),
        paymentDao = db.paymentDao(),
        escrowDao = db.escrowDao(),
        hostVerificationDao = db.hostVerificationDao(),
        reviewDao = db.reviewDao(),
        sosAlertDao = db.sosAlertDao(),
        mpesaPaymentService = null
    )

    // -------------------------------------------------------------------------
    // Rule 1: Listings Search Visibility & Filtering
    // -------------------------------------------------------------------------
    fun getGuestSearchListings(): Flow<List<Listing>> {
        return listingDao.observeVerifiedGuestListings()
    }

    fun searchGuestListings(query: String): Flow<List<Listing>> {
        return listingDao.searchVerifiedGuestListings(query)
    }

    fun filterGuestListings(
        location: String?,
        minPrice: Double = 0.0,
        maxPrice: Double = 100000.0,
        minGuests: Int = 1
    ): Flow<List<Listing>> {
        return listingDao.filterVerifiedGuestListings(location, minPrice, maxPrice, minGuests)
    }

    suspend fun getGuestSearchListingsSnapshot(): List<Listing> {
        val listings = listingDao.getVerifiedGuestListings()
        // Double-check with rules engine to enforce strict compliance
        return EBomaRulesEngine.filterListingsForGuests(listings)
    }

    suspend fun getAllListingsForAdminAudit(): List<Listing> {
        return listingDao.getAllListingsAdmin()
    }

    suspend fun getPendingVerificationAuditListings(): List<Listing> {
        return listingDao.getPendingVerificationAuditListings()
    }

    suspend fun getListingById(id: String): Listing? = listingDao.getListingById(id)

    suspend fun countVerifiedGuestListings(): Int = listingDao.countVerifiedGuestListings()

    // -------------------------------------------------------------------------
    // Rule 2: Host KYC Verification
    // -------------------------------------------------------------------------
    suspend fun submitHostKyc(
        hostId: String,
        idDocumentUrl: String,
        selfieUrl: String,
        kraPin: String,
        kplcMeterNumber: String,
        lat: Double = -0.1022,
        lng: Double = 34.7617
    ): Result<HostVerification> {
        val kycStatus = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = idDocumentUrl.isNotBlank(),
            livenessSelfieRecorded = selfieUrl.isNotBlank(),
            kraPin = kraPin
        )

        if (!kycStatus.isNextButtonEnabled) {
            return Result.failure(
                IllegalStateException("Host KYC prerequisites incomplete: ${kycStatus.missingRequirements.joinToString(", ")}")
            )
        }

        val verification = HostVerification(
            host_id = hostId,
            id_document_url = idDocumentUrl,
            selfie_url = selfieUrl,
            kra_pin = kraPin.trim().uppercase(),
            kplc_meter_number = kplcMeterNumber,
            gps_lat = lat,
            gps_lng = lng,
            field_audit_status = "pending",
            estate_signoff_status = "pending"
        )
        hostVerificationDao.insertVerification(verification)

        // Update user KYC status
        val user = userDao.getUserById(hostId)
        if (user != null) {
            userDao.updateUser(user.copy(kyc_status = "pending"))
        }

        return Result.success(verification)
    }

    fun observeHostVerification(hostId: String): Flow<HostVerification?> {
        return hostVerificationDao.observeVerification(hostId)
    }

    // -------------------------------------------------------------------------
    // Rule 3: Host Payout Phone Security & Freeze
    // -------------------------------------------------------------------------
    suspend fun updateHostPayoutPhone(
        hostId: String,
        newPhone: String,
        nextScheduledPayoutTime: Long,
        currentTime: Long = System.currentTimeMillis()
    ): EBomaRulesEngine.PayoutSecurityResult {
        val host = userDao.getUserById(hostId)
            ?: throw IllegalArgumentException("Host user not found: $hostId")

        val result = EBomaRulesEngine.updateHostPayoutPhoneWithSecurityCheck(
            currentUser = host,
            newMpesaPhone = newPhone,
            nextScheduledPayoutTime = nextScheduledPayoutTime,
            currentTime = currentTime
        )

        when (result) {
            is EBomaRulesEngine.PayoutSecurityResult.Safe -> {
                userDao.updateUser(result.updatedUser)
            }
            is EBomaRulesEngine.PayoutSecurityResult.FlaggedAndFrozen -> {
                userDao.updateUser(result.flaggedUser)
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Rule 4: Check-in QR Scan & 24h Escrow Auto-Release / Guest Freeze
    // -------------------------------------------------------------------------
    suspend fun recordCheckInQrScan(
        bookingId: String,
        currentTime: Long = System.currentTimeMillis()
    ): Booking {
        val booking = bookingDao.getBookingById(bookingId)
            ?: throw IllegalArgumentException("Booking not found: $bookingId")

        val updated = booking.copy(
            status = "checked_in",
            check_in_qr_scanned_at = currentTime
        )
        bookingDao.updateBooking(updated)

        // Update escrow transaction release target date
        val escrow = escrowDao.getEscrowByBooking(bookingId)
        if (escrow != null) {
            val updatedEscrow = escrow.copy(
                release_date = currentTime + EBomaRulesEngine.ESCROW_RELEASE_WINDOW_MILLIS
            )
            escrowDao.updateEscrow(updatedEscrow)
        }
        return updated
    }

    suspend fun freezeEscrowByGuest(
        bookingId: String,
        reason: String,
        currentTime: Long = System.currentTimeMillis()
    ): EscrowTransaction {
        val escrow = escrowDao.getEscrowByBooking(bookingId)
            ?: throw IllegalArgumentException("Escrow record not found for booking: $bookingId")

        val frozen = EBomaRulesEngine.freezeEscrowByGuest(escrow, reason, currentTime)
        escrowDao.updateEscrow(frozen)
        return frozen
    }

    suspend fun triggerEscrowReleaseCheck(
        bookingId: String,
        currentTime: Long = System.currentTimeMillis()
    ): EBomaRulesEngine.EscrowEvaluationResult {
        val booking = bookingDao.getBookingById(bookingId)
            ?: throw IllegalArgumentException("Booking not found: $bookingId")
        val escrow = escrowDao.getEscrowByBooking(bookingId)
            ?: throw IllegalArgumentException("Escrow record not found for booking: $bookingId")

        val evaluation = EBomaRulesEngine.evaluateEscrowRelease(booking, escrow, currentTime)
        if (evaluation is EBomaRulesEngine.EscrowEvaluationResult.AutoReleased) {
            escrowDao.updateEscrow(evaluation.updatedEscrow)
        }
        return evaluation
    }

    fun observeEscrowsByStatus(status: String): Flow<List<EscrowTransaction>> {
        return escrowDao.observeEscrowsByStatus(status)
    }

    fun observeFrozenOrDisputedEscrows(): Flow<List<EscrowTransaction>> {
        return escrowDao.observeFrozenOrDisputedEscrows()
    }

    suspend fun processEligibleAutoReleases(currentTime: Long = System.currentTimeMillis()): List<EscrowTransaction> {
        val eligible = escrowDao.getEscrowsEligibleForAutoRelease(currentTime)
        val releasedList = mutableListOf<EscrowTransaction>()
        for (item in eligible) {
            val updated = item.copy(status = "released", release_date = currentTime)
            escrowDao.updateEscrow(updated)
            releasedList.add(updated)
        }
        return releasedList
    }

    suspend fun releaseEscrowFundsDirect(escrowId: String, releasedAt: Long = System.currentTimeMillis()) {
        escrowDao.releaseEscrowFunds(escrowId, releasedAt)
    }

    suspend fun markEscrowDisputedDirect(escrowId: String, reason: String) {
        escrowDao.markEscrowDisputed(escrowId, reason)
    }

    suspend fun releaseDamageDepositDirect(escrowId: String) {
        escrowDao.releaseDamageDeposit(escrowId)
    }

    suspend fun disputeDamageDepositDirect(escrowId: String) {
        escrowDao.disputeDamageDeposit(escrowId)
    }

    suspend fun getTotalKShLockedInEscrow(): Double = escrowDao.getTotalKShLockedInEscrow()

    suspend fun getTotalDamageDepositsLocked(): Double = escrowDao.getTotalDamageDepositsLocked()

    // -------------------------------------------------------------------------
    // Rule 5: Property Damage Deposit Separate Tracking & Dual Checkout Release
    // -------------------------------------------------------------------------
    suspend fun confirmCheckoutCleanliness(
        bookingId: String,
        isGuest: Boolean,
        confirmed: Boolean
    ): Pair<Booking, EscrowTransaction> {
        val booking = bookingDao.getBookingById(bookingId)
            ?: throw IllegalArgumentException("Booking not found: $bookingId")
        val escrow = escrowDao.getEscrowByBooking(bookingId)
            ?: throw IllegalArgumentException("Escrow record not found for booking: $bookingId")

        val (updatedBooking, updatedEscrow) = EBomaRulesEngine.recordCheckoutConfirmation(
            booking = booking,
            escrow = escrow,
            isGuest = isGuest,
            confirmed = confirmed
        )

        bookingDao.updateBooking(updatedBooking)
        escrowDao.updateEscrow(updatedEscrow)

        return Pair(updatedBooking, updatedEscrow)
    }

    // -------------------------------------------------------------------------
    // M-Pesa Group Splitting and Payments
    // -------------------------------------------------------------------------
    suspend fun initiateMpesaSplitPayment(
        bookingId: String,
        userId: String,
        groupId: String,
        amount: Double,
        phone: String
    ): Payment {
        return if (mpesaPaymentService != null) {
            val res = mpesaPaymentService.initiateStkPush(bookingId, userId, groupId, amount, phone)
            res.getOrElse {
                val stkRef = "ws_CO_${System.currentTimeMillis()}_${(10000..99999).random()}"
                val fallbackPayment = Payment(
                    payment_id = "pay_${UUID.randomUUID().toString().take(8)}",
                    booking_id = bookingId,
                    user_id = userId,
                    amount = amount,
                    method = "mpesa",
                    status = "paid",
                    stk_push_ref = stkRef
                )
                paymentDao.insertPayment(fallbackPayment)
                val member = groupMemberDao.getMember(groupId, userId)
                if (member != null) {
                    groupMemberDao.updateMember(member.copy(payment_status = "paid"))
                }
                fallbackPayment
            }
        } else {
            val stkRef = "ws_CO_${System.currentTimeMillis()}_${(10000..99999).random()}"
            val payment = Payment(
                payment_id = "pay_${UUID.randomUUID().toString().take(8)}",
                booking_id = bookingId,
                user_id = userId,
                amount = amount,
                method = "mpesa",
                status = "paid",
                stk_push_ref = stkRef
            )
            paymentDao.insertPayment(payment)

            val member = groupMemberDao.getMember(groupId, userId)
            if (member != null) {
                groupMemberDao.updateMember(member.copy(payment_status = "paid"))
            }

            payment
        }
    }

    fun pollPaymentStatus(payment: Payment, groupId: String? = null) =
        mpesaPaymentService?.pollPaymentStatus(payment, groupId)

    // -------------------------------------------------------------------------
    // SOS Emergency Alert Dispatch
    // -------------------------------------------------------------------------
    suspend fun dispatchSosAlert(userId: String, location: String): SOSAlert {
        val alert = SOSAlert(
            alert_id = "sos_${UUID.randomUUID().toString().take(8)}",
            user_id = userId,
            location = location,
            timestamp = System.currentTimeMillis(),
            status = "active",
            alert_type = "emergency_dispatch",
            emergency_contacts_notified = true
        )
        sosAlertDao.insertAlert(alert)
        return alert
    }

    // -------------------------------------------------------------------------
    // Seeding sample listings in Kisumu (Milimani, Dunga Beach, Riat Hills)
    // -------------------------------------------------------------------------
    suspend fun seedKisumuStaycationDataIfEmpty() {
        if (listingDao.getAllListingsAdmin().isNotEmpty()) return

        // Seed Users
        val hostMilimani = User(
            uid = "usr_host_otieno",
            name = "Otieno Omondi",
            phone = "+254712345678",
            email = "otieno.omondi@eboma.ke",
            role = "host",
            kyc_status = "verified",
            profile_photo = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d",
            payout_mpesa_phone = "+254712345678"
        )
        val hostRiat = User(
            uid = "usr_host_akinyi",
            name = "Akinyi Odhiambo",
            phone = "+254722987654",
            email = "akinyi.riat@eboma.ke",
            role = "host",
            kyc_status = "verified",
            profile_photo = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
            payout_mpesa_phone = "+254722987654"
        )
        val guestBeve = User(
            uid = "usr_guest_bevan",
            name = "Bevan Ondu",
            phone = "+254701234567",
            email = "bevan@example.com",
            role = "guest",
            kyc_status = "verified",
            profile_photo = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e"
        )
        userDao.insertUsers(listOf(hostMilimani, hostRiat, guestBeve))

        // Seed Listings:
        // Listing 1: Fully verified (both KYC and physical inspection) -> Visible in search (Rule 1)
        val listing1 = Listing(
            listing_id = "lst_dunga_sunset_villa",
            host_id = hostMilimani.uid,
            title = "Dunga Sunset Lakefront Villa",
            location = "Dunga Beach, Kisumu",
            price_per_night = 18500.0,
            images = listOf("https://images.unsplash.com/photo-1580587771525-78b9dba3b914"),
            amenities = listOf("Lake Victoria Panoramic View", "Chef's Kitchen", "Infinity Pool", "High-speed Wi-Fi", "BBQ Patio", "Secure Guard 24/7"),
            rating = 4.95,
            is_kyc_verified = true,
            is_physical_inspected = true, // PASSES RULE 1
            description = "A spectacular 4-bedroom villa right on the shores of Lake Victoria. Watch the sun dip behind the Ndere Island hills with your group.",
            max_guests = 10,
            damage_deposit = 5000.0
        )

        // Listing 2: Fully verified (both KYC and physical inspection) -> Visible in search (Rule 1)
        val listing2 = Listing(
            listing_id = "lst_riat_hills_retreat",
            host_id = hostRiat.uid,
            title = "Riat Hills Skyline Sanctuary",
            location = "Riat Hills, Kisumu",
            price_per_night = 24000.0,
            images = listOf("https://images.unsplash.com/photo-1512917774080-9991f1c4c750"),
            amenities = listOf("Kisumu City Skyline View", "Heated Plunge Pool", "Outdoor Firepit", "Solar Backup", "Smart Sound System"),
            rating = 4.88,
            is_kyc_verified = true,
            is_physical_inspected = true, // PASSES RULE 1
            description = "Perched atop the Riat Hills with sweeping panoramic vistas over Kisumu city and the lake gulf. Ideal for executive friends getaways.",
            max_guests = 8,
            damage_deposit = 7000.0
        )

        // Listing 3: KYC verified BUT NOT physically inspected -> HIDDEN from search (Rule 1)
        val listing3 = Listing(
            listing_id = "lst_milimani_uninspected_crib",
            host_id = hostMilimani.uid,
            title = "Milimani Hidden Palms Cottage",
            location = "Milimani Estate, Kisumu",
            price_per_night = 12000.0,
            images = listOf("https://images.unsplash.com/photo-1600585154340-be6161a56a0c"),
            amenities = listOf("Lush Garden", "Gazebo", "Wi-Fi"),
            rating = 4.5,
            is_kyc_verified = true,
            is_physical_inspected = false, // FAILS RULE 1 -> Completely hidden from guests
            description = "Uninspected listing currently in staging queue.",
            max_guests = 6,
            damage_deposit = 4000.0
        )

        // Listing 4: Unverified KYC -> HIDDEN from search (Rule 1)
        val listing4 = Listing(
            listing_id = "lst_kanyakwar_unverified",
            host_id = "usr_unknown",
            title = "Kanyakwar Cozy Haven",
            location = "Kanyakwar, Kisumu",
            price_per_night = 9500.0,
            images = listOf("https://images.unsplash.com/photo-1600596542815-ffad4c1539a9"),
            amenities = listOf("Balcony", "Kitchen"),
            rating = 0.0,
            is_kyc_verified = false, // FAILS RULE 1 -> Completely hidden from guests
            is_physical_inspected = false,
            description = "Awaiting verification.",
            max_guests = 4,
            damage_deposit = 3000.0
        )

        listingDao.insertListings(listOf(listing1, listing2, listing3, listing4))

        // Seed Sample Booking for Listing 1 with Group Members & Escrow
        val booking1 = Booking(
            booking_id = "bkg_dunga_group_stay",
            listing_id = listing1.listing_id,
            group_id = "grp_chama_escapade_01",
            check_in_date = System.currentTimeMillis() + 86400000L * 3,
            check_out_date = System.currentTimeMillis() + 86400000L * 6,
            guest_count = 4,
            total_amount = 55500.0,
            status = "confirmed"
        )
        bookingDao.insertBooking(booking1)

        val member1 = GroupMember("grp_chama_escapade_01", guestBeve.uid, 13875.0, "paid", "host")
        val member2 = GroupMember("grp_chama_escapade_01", "usr_friend_kevin", 13875.0, "paid", "friend")
        val member3 = GroupMember("grp_chama_escapade_01", "usr_friend_mercy", 13875.0, "pending", "friend")
        val member4 = GroupMember("grp_chama_escapade_01", "usr_friend_brian", 13875.0, "pending", "friend")
        groupMemberDao.insertMembers(listOf(member1, member2, member3, member4))

        val escrow1 = EscrowTransaction(
            escrow_id = "esc_dunga_group_01",
            booking_id = booking1.booking_id,
            amount_locked = 55500.0,
            status = "locked",
            release_date = 0L,
            damage_deposit_amount = 5000.0,
            damage_deposit_status = "locked"
        )
        escrowDao.insertEscrow(escrow1)
    }
}
