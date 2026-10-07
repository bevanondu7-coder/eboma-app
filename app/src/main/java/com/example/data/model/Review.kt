package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Review entity submitted by group guests after stay completion.
 * Primary Key: review_id
 * Foreign Keys:
 * - booking_id -> Booking(booking_id)
 * - user_id -> User(uid)
 */
@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = Booking::class,
            parentColumns = ["booking_id"],
            childColumns = ["booking_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["booking_id"]),
        Index(value = ["user_id"]),
        Index(value = ["rating"])
    ]
)
data class Review(
    @PrimaryKey
    val review_id: String,
    val booking_id: String,
    val user_id: String,
    val rating: Double,
    val tags: List<String>, // e.g. ["Sunset View", "Fast Wi-Fi", "Lakeside Breeze", "Secure Compound"]
    val comment: String,
    val created_at: Long = System.currentTimeMillis()
)
