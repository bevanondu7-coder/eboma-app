package com.example.domain.rules

import com.example.data.model.Booking
import com.example.data.model.EscrowTransaction
import com.example.data.model.HostVerification
import com.example.data.model.Listing
import com.example.data.model.User

/**
 * Core business rules engine for e-Boma (Kisumu staycation platform).
 *
 * Implements the 5 mandatory business rules:
 * 1. Listings visibility rule (KYC verified AND physical inspected).
 * 2. Host KYC 3-check validation for "Next" button enablement.
 * 3. Host payout phone security freeze before scheduled payouts.
 * 4. Automatic escrow release 24 hours post-QR check-in with guest freeze window.
 * 5. Independent damage deposit release contingent on dual clean checkout confirmation.
 */
object EBomaRulesEngine {

    const val ESCROW_RELEASE_WINDOW_MILLIS: Long = 24 * 60 * 60 * 1000L // 24 Hours
    const val PAYOUT_SECURITY_THRESHOLD_MILLIS: Long = 48 * 60 * 60 * 1000L // 48 Hours before payout

    // =========================================================================
    // RULE 1: Listing Search Results Visibility
    // "A listing only appears in search results if BOTH is_kyc_verified is true
    // AND is_physical_inspected is true. Unverified listings stay completely hidden."
    // =========================================================================

    /**
     * Rule 1: A listing only appears in search results if BOTH is_kyc_verified is true
     * AND is_physical_inspected is true.
     */
    fun isListingEligibleForGuestSearch(listing: Listing): Boolean {
        return listing.is_kyc_verified && listing.is_physical_inspected
    }

    /**
     * Rule 1: Filters a collection of listings to only those eligible for guest display.
     */
    fun filterListingsForGuests(listings: List<Listing>): List<Listing> {
        return listings.filter { isListingEligibleForGuestSearch(it) }
    }

    // =========================================================================
    // RULE 2: Host KYC Three Required Checks
    // "Host KYC is three required checks — ID document scan, a recorded liveness
    // selfie, and a KRA PIN entry — and the 'Next' button on that screen stays
    // disabled until all three are complete."
    // =========================================================================

    data class HostKycStatus(
        val hasIdDocument: Boolean,
        val hasLivenessSelfie: Boolean,
        val hasValidKraPin: Boolean,
        val isNextButtonEnabled: Boolean,
        val missingRequirements: List<String>
    )

    /**
     * Rule 2: Evaluates whether all three KYC checks are complete to enable the Next button.
     */
    fun evaluateHostKycCompletion(
        idDocumentScanned: Boolean,
        livenessSelfieRecorded: Boolean,
        kraPin: String?
    ): HostKycStatus {
        val hasId = idDocumentScanned
        val hasSelfie = livenessSelfieRecorded
        val cleanedPin = kraPin?.trim().orEmpty()
        // KRA PIN standard Kenyan format check: typically starts with letter, digits, ends with letter
        val hasKraPin = cleanedPin.isNotBlank() && cleanedPin.length >= 8

        val missing = mutableListOf<String>()
        if (!hasId) missing.add("National ID / Passport document scan")
        if (!hasSelfie) missing.add("Recorded liveness selfie video/photo")
        if (!hasKraPin) missing.add("Valid KRA PIN entry")

        val canProceed = hasId && hasSelfie && hasKraPin

        return HostKycStatus(
            hasIdDocument = hasId,
            hasLivenessSelfie = hasSelfie,
            hasValidKraPin = hasKraPin,
            isNextButtonEnabled = canProceed,
            missingRequirements = missing
        )
    }

    /**
     * Rule 2 helper for HostVerification entity.
     */
    fun isHostVerificationReady(verification: HostVerification): Boolean {
        return evaluateHostKycCompletion(
            idDocumentScanned = verification.id_document_url.isNotBlank(),
            livenessSelfieRecorded = verification.selfie_url.isNotBlank(),
            kraPin = verification.kra_pin
        ).isNextButtonEnabled
    }

    // =========================================================================
    // RULE 3: Payout M-Pesa Number Change Fraud Detection
    // "If a host's payout M-Pesa number changes shortly before a scheduled payout,
    // automatically flag the account and freeze the payout until the host re-verifies identity."
    // =========================================================================

    sealed class PayoutSecurityResult {
        data class Safe(val updatedUser: User) : PayoutSecurityResult()
        data class FlaggedAndFrozen(
            val flaggedUser: User,
            val reason: String,
            val hoursUntilPayout: Double
        ) : PayoutSecurityResult()
    }

    /**
     * Rule 3: Checks if updating the host payout phone happens within the sensitive window
     * before an upcoming scheduled payout. If so, automatically flags and freezes.
     */
    fun updateHostPayoutPhoneWithSecurityCheck(
        currentUser: User,
        newMpesaPhone: String,
        nextScheduledPayoutTime: Long,
        currentTime: Long = System.currentTimeMillis(),
        thresholdMillis: Long = PAYOUT_SECURITY_THRESHOLD_MILLIS
    ): PayoutSecurityResult {
        // Only evaluate if phone is actually different
        val isPhoneChanged = currentUser.payout_mpesa_phone != newMpesaPhone
        if (!isPhoneChanged) {
            return PayoutSecurityResult.Safe(currentUser)
        }

        val timeUntilPayout = nextScheduledPayoutTime - currentTime
        val isShortlyBeforePayout = nextScheduledPayoutTime > 0 &&
                timeUntilPayout in 0..thresholdMillis

        return if (isShortlyBeforePayout) {
            val hoursUntil = timeUntilPayout.toDouble() / (1000 * 60 * 60)
            val flagReason = "Payout M-Pesa number changed within ${String.format("%.1f", hoursUntil)} hours of scheduled payout. Account flagged for security; payout frozen pending identity re-verification."
            val flaggedUser = currentUser.copy(
                payout_mpesa_phone = newMpesaPhone,
                payout_phone_updated_at = currentTime,
                kyc_status = "flagged_reverification",
                is_payout_frozen = true,
                payout_flag_reason = flagReason
            )
            PayoutSecurityResult.FlaggedAndFrozen(
                flaggedUser = flaggedUser,
                reason = flagReason,
                hoursUntilPayout = hoursUntil
            )
        } else {
            val updatedUser = currentUser.copy(
                payout_mpesa_phone = newMpesaPhone,
                payout_phone_updated_at = currentTime,
                is_payout_frozen = false,
                payout_flag_reason = null
            )
            PayoutSecurityResult.Safe(updatedUser)
        }
    }

    // =========================================================================
    // RULE 4: Escrow Release 24h Post QR Check-In with Guest Freeze Protection
    // "Escrow funds release automatically 24 hours after a successful check-in QR scan,
    // unless the guest taps 'Freeze Escrow' during that 24-hour window."
    // =========================================================================

    sealed class EscrowEvaluationResult {
        data class AutoReleased(val updatedEscrow: EscrowTransaction) : EscrowEvaluationResult()
        data class FrozenByGuest(val escrow: EscrowTransaction, val reason: String) : EscrowEvaluationResult()
        data class PendingRelease(
            val escrow: EscrowTransaction,
            val remainingMillis: Long
        ) : EscrowEvaluationResult()
        data class NotCheckedIn(val escrow: EscrowTransaction) : EscrowEvaluationResult()
    }

    /**
     * Rule 4: Guest taps "Freeze Escrow" during the 24-hour post check-in window.
     */
    fun freezeEscrowByGuest(
        escrow: EscrowTransaction,
        reason: String,
        currentTime: Long = System.currentTimeMillis()
    ): EscrowTransaction {
        return escrow.copy(
            status = "frozen",
            is_frozen_by_guest = true,
            frozen_at = currentTime,
            freeze_reason = reason
        )
    }

    /**
     * Rule 4: Evaluates whether escrow should be automatically released, held as pending,
     * or remain frozen.
     */
    fun evaluateEscrowRelease(
        booking: Booking,
        escrow: EscrowTransaction,
        currentTime: Long = System.currentTimeMillis()
    ): EscrowEvaluationResult {
        // If guest tapped "Freeze Escrow", funds cannot auto-release
        if (escrow.is_frozen_by_guest || escrow.status == "frozen" || escrow.status == "disputed") {
            return EscrowEvaluationResult.FrozenByGuest(
                escrow = escrow,
                reason = escrow.freeze_reason ?: "Escrow frozen by guest dispute"
            )
        }

        // Check if QR scan has occurred
        val qrScanTime = booking.check_in_qr_scanned_at
        if (qrScanTime == null) {
            return EscrowEvaluationResult.NotCheckedIn(escrow)
        }

        val autoReleaseTargetTime = qrScanTime + ESCROW_RELEASE_WINDOW_MILLIS
        return if (currentTime >= autoReleaseTargetTime) {
            // 24 hours elapsed without freeze: auto-release
            val releasedEscrow = escrow.copy(
                status = "released",
                release_date = currentTime
            )
            EscrowEvaluationResult.AutoReleased(releasedEscrow)
        } else {
            val remainingMillis = autoReleaseTargetTime - currentTime
            EscrowEvaluationResult.PendingRelease(escrow, remainingMillis)
        }
    }

    // =========================================================================
    // RULE 5: Damage Deposit Separate Tracking & Dual Clean Checkout Release
    // "The property damage deposit is tracked separately from the main booking amount
    // and only releases after both guest and host confirm a clean checkout."
    // =========================================================================

    sealed class DamageDepositReleaseResult {
        data class Released(val updatedEscrow: EscrowTransaction) : DamageDepositReleaseResult()
        data class WaitingForBoth(val remainingConfirmations: List<String>) : DamageDepositReleaseResult()
        data class Disputed(val updatedEscrow: EscrowTransaction, val reason: String) : DamageDepositReleaseResult()
    }

    /**
     * Rule 5: Evaluates damage deposit release status based on separate tracking
     * and mutual confirmation from both guest and host.
     */
    fun evaluateDamageDepositRelease(
        booking: Booking,
        escrow: EscrowTransaction
    ): DamageDepositReleaseResult {
        if (escrow.damage_deposit_status == "disputed") {
            return DamageDepositReleaseResult.Disputed(
                updatedEscrow = escrow,
                reason = "Damage claim filed for deposit investigation."
            )
        }

        val guestConfirmed = booking.is_clean_checkout_guest_confirmed
        val hostConfirmed = booking.is_clean_checkout_host_confirmed

        return if (guestConfirmed && hostConfirmed) {
            val updated = escrow.copy(
                damage_deposit_status = "released"
            )
            DamageDepositReleaseResult.Released(updated)
        } else {
            val pendingList = mutableListOf<String>()
            if (!guestConfirmed) pendingList.add("Guest checkout sign-off")
            if (!hostConfirmed) pendingList.add("Host property inspection sign-off")
            DamageDepositReleaseResult.WaitingForBoth(pendingList)
        }
    }

    /**
     * Rule 5 helper: Updates guest or host confirmation on booking and triggers deposit evaluation.
     */
    fun recordCheckoutConfirmation(
        booking: Booking,
        escrow: EscrowTransaction,
        isGuest: Boolean,
        confirmed: Boolean
    ): Pair<Booking, EscrowTransaction> {
        val updatedBooking = if (isGuest) {
            booking.copy(is_clean_checkout_guest_confirmed = confirmed)
        } else {
            booking.copy(is_clean_checkout_host_confirmed = confirmed)
        }

        val depositResult = evaluateDamageDepositRelease(updatedBooking, escrow)
        val updatedEscrow = when (depositResult) {
            is DamageDepositReleaseResult.Released -> depositResult.updatedEscrow
            is DamageDepositReleaseResult.Disputed -> depositResult.updatedEscrow
            is DamageDepositReleaseResult.WaitingForBoth -> escrow
        }

        return Pair(updatedBooking, updatedEscrow)
    }
}
