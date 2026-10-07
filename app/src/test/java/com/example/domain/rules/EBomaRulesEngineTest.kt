package com.example.domain.rules

import com.example.data.model.Booking
import com.example.data.model.EscrowTransaction
import com.example.data.model.Listing
import com.example.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EBomaRulesEngineTest {

    // =========================================================================
    // Rule 1 Tests: Listing Search Results Visibility
    // =========================================================================
    @Test
    fun rule1_listingVisibleOnlyIfBothKycVerifiedAndPhysicallyInspected() {
        val fullyVerified = Listing(
            listing_id = "l1",
            host_id = "h1",
            title = "Dunga Villa",
            location = "Dunga Beach, Kisumu",
            price_per_night = 15000.0,
            images = listOf("img1.jpg"),
            amenities = listOf("Pool"),
            rating = 4.8,
            is_kyc_verified = true,
            is_physical_inspected = true
        )
        val kycOnly = fullyVerified.copy(listing_id = "l2", is_kyc_verified = true, is_physical_inspected = false)
        val inspectionOnly = fullyVerified.copy(listing_id = "l3", is_kyc_verified = false, is_physical_inspected = true)
        val neither = fullyVerified.copy(listing_id = "l4", is_kyc_verified = false, is_physical_inspected = false)

        // Rule verification
        assertTrue("Fully verified listing must be visible", EBomaRulesEngine.isListingEligibleForGuestSearch(fullyVerified))
        assertFalse("Listing with KYC only must be hidden", EBomaRulesEngine.isListingEligibleForGuestSearch(kycOnly))
        assertFalse("Listing with inspection only must be hidden", EBomaRulesEngine.isListingEligibleForGuestSearch(inspectionOnly))
        assertFalse("Listing with neither must be hidden", EBomaRulesEngine.isListingEligibleForGuestSearch(neither))

        val allListings = listOf(fullyVerified, kycOnly, inspectionOnly, neither)
        val filtered = EBomaRulesEngine.filterListingsForGuests(allListings)

        assertEquals("Only 1 listing meets both conditions", 1, filtered.size)
        assertEquals("l1", filtered.first().listing_id)
    }

    // =========================================================================
    // Rule 2 Tests: Host KYC Three Required Checks
    // =========================================================================
    @Test
    fun rule2_hostKycNextButtonDisabledUntilAllThreeChecksAreComplete() {
        // Test all incomplete permutations
        val noneComplete = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = false,
            livenessSelfieRecorded = false,
            kraPin = null
        )
        assertFalse("Next button must be disabled when nothing complete", noneComplete.isNextButtonEnabled)
        assertEquals(3, noneComplete.missingRequirements.size)

        val idOnly = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = true,
            livenessSelfieRecorded = false,
            kraPin = null
        )
        assertFalse("Next button must be disabled when only ID scanned", idOnly.isNextButtonEnabled)

        val idAndSelfie = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = true,
            livenessSelfieRecorded = true,
            kraPin = ""
        )
        assertFalse("Next button must be disabled without KRA PIN", idAndSelfie.isNextButtonEnabled)

        val idAndPin = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = true,
            livenessSelfieRecorded = false,
            kraPin = "A012345678B"
        )
        assertFalse("Next button must be disabled without liveness selfie", idAndPin.isNextButtonEnabled)

        // All three complete
        val allComplete = EBomaRulesEngine.evaluateHostKycCompletion(
            idDocumentScanned = true,
            livenessSelfieRecorded = true,
            kraPin = "A012345678B"
        )
        assertTrue("Next button MUST be enabled when all 3 checks are done", allComplete.isNextButtonEnabled)
        assertTrue("No missing requirements remain", allComplete.missingRequirements.isEmpty())
    }

    // =========================================================================
    // Rule 3 Tests: Payout M-Pesa Number Change Shortly Before Scheduled Payout
    // =========================================================================
    @Test
    fun rule3_payoutPhoneChangeShortlyBeforePayoutFlagsAndFreezesAccount() {
        val baseUser = User(
            uid = "host_123",
            name = "Otieno Host",
            phone = "+254712000000",
            email = "otieno@eboma.ke",
            role = "host",
            kyc_status = "verified",
            profile_photo = "photo.jpg",
            payout_mpesa_phone = "+254712000000"
        )

        val now = 1000000000L
        val payoutIn6Hours = now + (6 * 3600 * 1000L) // Within 48-hour threshold
        val payoutIn72Hours = now + (72 * 3600 * 1000L) // Outside 48-hour threshold

        // Case A: Host changes payout phone within 6 hours of scheduled payout -> FLAG AND FREEZE
        val resultSuspicious = EBomaRulesEngine.updateHostPayoutPhoneWithSecurityCheck(
            currentUser = baseUser,
            newMpesaPhone = "+254799999999",
            nextScheduledPayoutTime = payoutIn6Hours,
            currentTime = now
        )

        assertTrue("Result must be FlaggedAndFrozen", resultSuspicious is EBomaRulesEngine.PayoutSecurityResult.FlaggedAndFrozen)
        val flaggedUser = (resultSuspicious as EBomaRulesEngine.PayoutSecurityResult.FlaggedAndFrozen).flaggedUser
        assertTrue("Payout must be frozen", flaggedUser.is_payout_frozen)
        assertEquals("flagged_reverification", flaggedUser.kyc_status)
        assertEquals("+254799999999", flaggedUser.payout_mpesa_phone)

        // Case B: Host changes payout phone far in advance (72 hours before) -> SAFE
        val resultSafe = EBomaRulesEngine.updateHostPayoutPhoneWithSecurityCheck(
            currentUser = baseUser,
            newMpesaPhone = "+254799999999",
            nextScheduledPayoutTime = payoutIn72Hours,
            currentTime = now
        )

        assertTrue("Result must be Safe", resultSafe is EBomaRulesEngine.PayoutSecurityResult.Safe)
        val safeUser = (resultSafe as EBomaRulesEngine.PayoutSecurityResult.Safe).updatedUser
        assertFalse("Payout should not be frozen", safeUser.is_payout_frozen)
        assertEquals("verified", safeUser.kyc_status)
    }

    // =========================================================================
    // Rule 4 Tests: 24-Hour Escrow Auto-Release & Guest Freeze Protection
    // =========================================================================
    @Test
    fun rule4_escrowReleases24HoursPostQrScanUnlessGuestFreezes() {
        val checkInTime = 1000000000L
        val booking = Booking(
            booking_id = "b1",
            listing_id = "l1",
            group_id = "g1",
            check_in_date = checkInTime,
            check_out_date = checkInTime + 86400000L * 3,
            guest_count = 4,
            total_amount = 40000.0,
            status = "checked_in",
            check_in_qr_scanned_at = checkInTime
        )

        val escrow = EscrowTransaction(
            escrow_id = "e1",
            booking_id = "b1",
            amount_locked = 40000.0,
            status = "locked",
            release_date = checkInTime + EBomaRulesEngine.ESCROW_RELEASE_WINDOW_MILLIS,
            damage_deposit_amount = 5000.0
        )

        // Subcase A: 12 hours after check-in -> Still within 24-hr window, pending
        val evalAt12Hours = EBomaRulesEngine.evaluateEscrowRelease(
            booking = booking,
            escrow = escrow,
            currentTime = checkInTime + (12 * 3600 * 1000L)
        )
        assertTrue("At 12 hours, escrow must be PendingRelease", evalAt12Hours is EBomaRulesEngine.EscrowEvaluationResult.PendingRelease)

        // Subcase B: 24.5 hours after check-in with NO freeze -> Auto-released!
        val evalAt25Hours = EBomaRulesEngine.evaluateEscrowRelease(
            booking = booking,
            escrow = escrow,
            currentTime = checkInTime + (25 * 3600 * 1000L)
        )
        assertTrue("At 25 hours without freeze, must be AutoReleased", evalAt25Hours is EBomaRulesEngine.EscrowEvaluationResult.AutoReleased)
        val releasedEscrow = (evalAt25Hours as EBomaRulesEngine.EscrowEvaluationResult.AutoReleased).updatedEscrow
        assertEquals("released", releasedEscrow.status)

        // Subcase C: Guest tapped "Freeze Escrow" during the window -> Block auto-release
        val frozenEscrow = EBomaRulesEngine.freezeEscrowByGuest(
            escrow = escrow,
            reason = "Listing had no running water contrary to description",
            currentTime = checkInTime + (10 * 3600 * 1000L)
        )
        assertTrue(frozenEscrow.is_frozen_by_guest)
        assertEquals("frozen", frozenEscrow.status)

        val evalFrozenAfter25Hours = EBomaRulesEngine.evaluateEscrowRelease(
            booking = booking,
            escrow = frozenEscrow,
            currentTime = checkInTime + (25 * 3600 * 1000L)
        )
        assertTrue("Frozen escrow must NEVER auto-release", evalFrozenAfter25Hours is EBomaRulesEngine.EscrowEvaluationResult.FrozenByGuest)
    }

    // =========================================================================
    // Rule 5 Tests: Damage Deposit Separate Tracking & Dual Clean Checkout
    // =========================================================================
    @Test
    fun rule5_damageDepositReleasesOnlyAfterBothGuestAndHostConfirmCleanCheckout() {
        val bookingInitial = Booking(
            booking_id = "b1",
            listing_id = "l1",
            group_id = "g1",
            check_in_date = 1000000000L,
            check_out_date = 1000000000L + 86400000L * 3,
            guest_count = 4,
            total_amount = 50000.0,
            status = "checked_out",
            is_clean_checkout_guest_confirmed = false,
            is_clean_checkout_host_confirmed = false
        )

        val escrowInitial = EscrowTransaction(
            escrow_id = "e1",
            booking_id = "b1",
            amount_locked = 50000.0,
            status = "released", // Main stay amount released separately
            release_date = 1000000000L,
            damage_deposit_amount = 6000.0, // Tracked separately
            damage_deposit_status = "locked"
        )

        // Neither confirmed
        val res0 = EBomaRulesEngine.evaluateDamageDepositRelease(bookingInitial, escrowInitial)
        assertTrue("Neither confirmed: must be WaitingForBoth", res0 is EBomaRulesEngine.DamageDepositReleaseResult.WaitingForBoth)
        assertEquals(2, (res0 as EBomaRulesEngine.DamageDepositReleaseResult.WaitingForBoth).remainingConfirmations.size)

        // Only guest confirms
        val (bookingGuestOnly, escrowAfterGuest) = EBomaRulesEngine.recordCheckoutConfirmation(
            booking = bookingInitial,
            escrow = escrowInitial,
            isGuest = true,
            confirmed = true
        )
        val res1 = EBomaRulesEngine.evaluateDamageDepositRelease(bookingGuestOnly, escrowAfterGuest)
        assertTrue("Guest only: must still be WaitingForBoth", res1 is EBomaRulesEngine.DamageDepositReleaseResult.WaitingForBoth)
        assertEquals(1, (res1 as EBomaRulesEngine.DamageDepositReleaseResult.WaitingForBoth).remainingConfirmations.size)
        assertEquals("locked", escrowAfterGuest.damage_deposit_status)

        // Now host also confirms clean checkout -> Dual confirmation fulfilled!
        val (bookingBoth, escrowAfterBoth) = EBomaRulesEngine.recordCheckoutConfirmation(
            booking = bookingGuestOnly,
            escrow = escrowAfterGuest,
            isGuest = false,
            confirmed = true
        )
        val res2 = EBomaRulesEngine.evaluateDamageDepositRelease(bookingBoth, escrowAfterBoth)
        assertTrue("Both confirmed: damage deposit released!", res2 is EBomaRulesEngine.DamageDepositReleaseResult.Released)
        assertEquals("released", escrowAfterBoth.damage_deposit_status)
        assertEquals(6000.0, escrowAfterBoth.damage_deposit_amount, 0.001)
    }
}
