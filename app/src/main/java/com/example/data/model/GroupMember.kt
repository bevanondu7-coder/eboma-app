package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * GroupMember entity representing individual friends splitting the staycation cost.
 * Primary Keys: ["group_id", "user_id"]
 * Foreign Key: user_id -> User(uid)
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "user_id"],
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
        Index(value = ["group_id"]),
        Index(value = ["payment_status"])
    ]
)
data class GroupMember(
    val group_id: String,
    val user_id: String,
    val split_amount: Double, // In KSh
    val payment_status: String, // "pending", "paid", "failed"
    val role: String // "host", "friend"
)
