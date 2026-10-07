package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.example.data.model.Converters
import com.example.data.model.EscrowTransaction
import com.example.data.model.GroupMember
import com.example.data.model.HostVerification
import com.example.data.model.Listing
import com.example.data.model.Payment
import com.example.data.model.Review
import com.example.data.model.SOSAlert
import com.example.data.model.User

@Database(
    entities = [
        User::class,
        Listing::class,
        Booking::class,
        GroupMember::class,
        Payment::class,
        EscrowTransaction::class,
        HostVerification::class,
        Review::class,
        SOSAlert::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EBomaDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun listingDao(): ListingDao
    abstract fun bookingDao(): BookingDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun paymentDao(): PaymentDao
    abstract fun escrowDao(): EscrowDao
    abstract fun hostVerificationDao(): HostVerificationDao
    abstract fun reviewDao(): ReviewDao
    abstract fun sosAlertDao(): SOSAlertDao

    companion object {
        @Volatile
        private var INSTANCE: EBomaDatabase? = null

        fun getDatabase(context: Context): EBomaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EBomaDatabase::class.java,
                    "eboma_kisumu_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
