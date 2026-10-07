package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Payment entity representing M-Pesa STK push and payment receipts for group contributions.
 * Primary Key: payment_id
 * Foreign Keys:
 * - booking_id -> Booking(booking_id)
 * - user_id -> User(uid)
 */
@Entity(
    tableName = "payments",
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
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ]
)
data class Payment(
    @PrimaryKey
    val payment_id: String,
    val booking_id: String,
    val user_id: String,
    val amount: Double, // In KSh
    val method: String = "mpesa", // "mpesa"
    val status: String, // "pending", "paid", "failed"
    val stk_push_ref: String, // e.g., "ws_CO_07102026_987654321"
    val created_at: Long = System.currentTimeMillis()
)
