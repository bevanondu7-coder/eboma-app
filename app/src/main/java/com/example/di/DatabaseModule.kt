package com.example.di

import android.content.Context
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EBomaDatabase {
        return EBomaDatabase.getDatabase(context)
    }

    @Provides
    fun provideUserDao(database: EBomaDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideListingDao(database: EBomaDatabase): ListingDao {
        return database.listingDao()
    }

    @Provides
    fun provideBookingDao(database: EBomaDatabase): BookingDao {
        return database.bookingDao()
    }

    @Provides
    fun provideGroupMemberDao(database: EBomaDatabase): GroupMemberDao {
        return database.groupMemberDao()
    }

    @Provides
    fun providePaymentDao(database: EBomaDatabase): PaymentDao {
        return database.paymentDao()
    }

    @Provides
    fun provideEscrowDao(database: EBomaDatabase): EscrowDao {
        return database.escrowDao()
    }

    @Provides
    fun provideHostVerificationDao(database: EBomaDatabase): HostVerificationDao {
        return database.hostVerificationDao()
    }

    @Provides
    fun provideReviewDao(database: EBomaDatabase): ReviewDao {
        return database.reviewDao()
    }

    @Provides
    fun provideSOSAlertDao(database: EBomaDatabase): SOSAlertDao {
        return database.sosAlertDao()
    }
}
