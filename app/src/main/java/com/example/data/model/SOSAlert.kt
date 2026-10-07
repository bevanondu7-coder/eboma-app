package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SOSAlert entity for emergency and safety dispatches in Kisumu.
 * Primary Key: alert_id
 * Foreign Key: user_id -> User(uid)
 */
@Entity(
    tableName = "sos_alerts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["status"]),
        Index(value = ["timestamp"])
    ]
)
data class SOSAlert(
    @PrimaryKey
    val alert_id: String,
    val user_id: String,
    val location: String, // e.g. "Dunga Hill Villa, Kisumu - Lat: -0.134, Lng: 34.739"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "active", "dispatched", "resolved"
    val alert_type: String = "security_emergency",
    val emergency_contacts_notified: Boolean = true
)
