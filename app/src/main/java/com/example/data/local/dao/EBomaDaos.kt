package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Booking
import com.example.data.model.GroupMember
import com.example.data.model.HostVerification
import com.example.data.model.Payment
import com.example.data.model.Review
import com.example.data.model.SOSAlert
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUserById(uid: String): User?

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observeUser(uid: String): Flow<User?>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE role = 'host'")
    suspend fun getAllHosts(): List<User>

    @Query("SELECT * FROM users WHERE is_payout_frozen = 1")
    fun observePayoutFrozenHosts(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    /**
     * Rule 3 State Transition: Freeze host payout and flag account.
     */
    @Query("""
        UPDATE users 
        SET is_payout_frozen = 1, 
            kyc_status = 'flagged_reverification', 
            payout_flag_reason = :reason,
            payout_mpesa_phone = :newPhone,
            payout_phone_updated_at = :updatedAt
        WHERE uid = :uid
    """)
    suspend fun flagAndFreezeHostPayout(
        uid: String,
        newPhone: String,
        reason: String,
        updatedAt: Long
    )
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE booking_id = :bookingId")
    suspend fun getBookingById(bookingId: String): Booking?

    @Query("SELECT * FROM bookings WHERE booking_id = :bookingId")
    fun observeBooking(bookingId: String): Flow<Booking?>

    @Query("SELECT * FROM bookings WHERE group_id = :groupId")
    suspend fun getBookingsByGroup(groupId: String): List<Booking>

    @Query("SELECT * FROM bookings ORDER BY check_in_date DESC")
    suspend fun getAllBookings(): List<Booking>

    @Query("SELECT * FROM bookings WHERE status = :status")
    fun observeBookingsByStatus(status: String): Flow<List<Booking>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<Booking>)

    @Update
    suspend fun updateBooking(booking: Booking)

    /**
     * Rule 4 State Transition: Record check-in QR scan timestamp.
     */
    @Query("""
        UPDATE bookings 
        SET status = 'checked_in', 
            check_in_qr_scanned_at = :timestamp 
        WHERE booking_id = :bookingId
    """)
    suspend fun recordCheckInQrScan(bookingId: String, timestamp: Long)

    /**
     * Rule 5 State Transition: Guest confirms clean checkout.
     */
    @Query("""
        UPDATE bookings 
        SET is_clean_checkout_guest_confirmed = :confirmed 
        WHERE booking_id = :bookingId
    """)
    suspend fun setGuestCheckoutConfirmation(bookingId: String, confirmed: Boolean)

    /**
     * Rule 5 State Transition: Host confirms clean checkout.
     */
    @Query("""
        UPDATE bookings 
        SET is_clean_checkout_host_confirmed = :confirmed 
        WHERE booking_id = :bookingId
    """)
    suspend fun setHostCheckoutConfirmation(bookingId: String, confirmed: Boolean)
}

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getMembersByGroup(groupId: String): List<GroupMember>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMember>>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId AND user_id = :userId")
    suspend fun getMember(groupId: String, userId: String): GroupMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMember>)

    @Update
    suspend fun updateMember(member: GroupMember)

    @Query("UPDATE group_members SET payment_status = :status WHERE group_id = :groupId AND user_id = :userId")
    suspend fun updateMemberPaymentStatus(groupId: String, userId: String, status: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE booking_id = :bookingId ORDER BY created_at DESC")
    suspend fun getPaymentsByBooking(bookingId: String): List<Payment>

    @Query("SELECT * FROM payments WHERE booking_id = :bookingId ORDER BY created_at DESC")
    fun observePaymentsByBooking(bookingId: String): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE payment_id = :paymentId")
    suspend fun getPaymentById(paymentId: String): Payment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<Payment>)

    @Update
    suspend fun updatePayment(payment: Payment)
}

@Dao
interface HostVerificationDao {
    @Query("SELECT * FROM host_verifications WHERE host_id = :hostId")
    suspend fun getVerificationByHost(hostId: String): HostVerification?

    @Query("SELECT * FROM host_verifications WHERE host_id = :hostId")
    fun observeVerification(hostId: String): Flow<HostVerification?>

    @Query("SELECT * FROM host_verifications WHERE field_audit_status = 'pending'")
    suspend fun getPendingFieldAudits(): List<HostVerification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerification(verification: HostVerification)

    @Update
    suspend fun updateVerification(verification: HostVerification)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE booking_id = :bookingId")
    suspend fun getReviewsByBooking(bookingId: String): List<Review>

    @Query("SELECT * FROM reviews ORDER BY created_at DESC")
    suspend fun getAllReviews(): List<Review>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)
}

@Dao
interface SOSAlertDao {
    @Query("SELECT * FROM sos_alerts WHERE status = 'active' ORDER BY timestamp DESC")
    fun observeActiveAlerts(): Flow<List<SOSAlert>>

    @Query("SELECT * FROM sos_alerts WHERE alert_id = :alertId")
    suspend fun getAlertById(alertId: String): SOSAlert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: SOSAlert)

    @Update
    suspend fun updateAlert(alert: SOSAlert)

    @Query("UPDATE sos_alerts SET status = :newStatus WHERE alert_id = :alertId")
    suspend fun updateAlertStatus(alertId: String, newStatus: String)
}
