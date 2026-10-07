package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * HostVerification entity for Kisumu host compliance and security.
 * Primary Key: host_id
 * Foreign Key: host_id -> User(uid)
 *
 * Rule 2: Host KYC requires three mandatory checks:
 * 1. ID document scan (id_document_url is non-empty)
 * 2. Recorded liveness selfie (selfie_url is non-empty)
 * 3. KRA PIN entry (kra_pin is non-empty and valid)
 */
@Entity(
    tableName = "host_verifications",
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
        Index(value = ["field_audit_status"]),
        Index(value = ["estate_signoff_status"])
    ]
)
data class HostVerification(
    @PrimaryKey
    val host_id: String,
    val id_document_url: String,
    val selfie_url: String,
    val kra_pin: String, // Kenyan KRA PIN (e.g. A012345678B)
    val kplc_meter_number: String,
    val gps_lat: Double,
    val gps_lng: Double,
    val field_audit_status: String, // "pending", "passed", "failed"
    val estate_signoff_status: String // "pending", "approved", "rejected"
) {
    /**
     * Rule 2 logic: Evaluates if host KYC initial requirements are fully satisfied.
     */
    fun isKycPrerequisitesComplete(): Boolean {
        val hasIdDocument = id_document_url.isNotBlank()
        val hasLivenessSelfie = selfie_url.isNotBlank()
        val hasValidKraPin = kra_pin.isNotBlank() && kra_pin.trim().length >= 8
        return hasIdDocument && hasLivenessSelfie && hasValidKraPin
    }
}
