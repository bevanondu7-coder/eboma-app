package com.example.data.remote.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EBomaDatabase
import com.example.data.local.dao.BookingDao
import com.example.data.local.dao.GroupMemberDao
import com.example.data.local.dao.ListingDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.UserDao
import com.example.data.model.Booking
import com.example.data.model.GroupMember
import com.example.data.model.Listing
import com.example.data.model.User
import com.example.data.remote.api.MpesaApiService
import com.example.data.remote.model.MpesaAuthResponse
import com.example.data.remote.model.MpesaStkPushRequest
import com.example.data.remote.model.MpesaStkPushResponse
import com.example.data.remote.model.MpesaStkQueryRequest
import com.example.data.remote.model.MpesaStkQueryResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MpesaPaymentServiceTest {

    private lateinit var db: EBomaDatabase
    private lateinit var userDao: UserDao
    private lateinit var listingDao: ListingDao
    private lateinit var bookingDao: BookingDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var groupMemberDao: GroupMemberDao
    private lateinit var mpesaService: MpesaPaymentService

    private val fakeApiService = object : MpesaApiService {
        override suspend fun getAccessToken(basicAuthHeader: String, grantType: String): Response<MpesaAuthResponse> {
            return Response.success(MpesaAuthResponse("mock_token", "3599"))
        }

        override suspend fun sendStkPush(bearerTokenHeader: String, request: MpesaStkPushRequest): Response<MpesaStkPushResponse> {
            return Response.success(
                MpesaStkPushResponse(
                    merchantRequestId = "merch_123",
                    checkoutRequestId = "ws_CO_TEST_9999",
                    responseCode = "0",
                    responseDescription = "Success",
                    customerMessage = "Success"
                )
            )
        }

        override suspend fun queryStkStatus(bearerTokenHeader: String, request: MpesaStkQueryRequest): Response<MpesaStkQueryResponse> {
            return Response.success(
                MpesaStkQueryResponse(
                    responseCode = "0",
                    responseDescription = "Success",
                    merchantRequestId = "merch_123",
                    checkoutRequestId = request.checkoutRequestId,
                    resultCode = "0",
                    resultDesc = "The service request is processed successfully."
                )
            )
        }
    }

    private val testUser = User(
        uid = "usr_guest_mpesa",
        name = "Akinyi Friend",
        phone = "0712345678",
        email = "akinyi@example.com",
        role = "guest",
        kyc_status = "verified",
        profile_photo = "p.jpg"
    )

    private val testHost = User(
        uid = "usr_host_mpesa",
        name = "Otieno Host",
        phone = "0722000000",
        email = "host@example.com",
        role = "host",
        kyc_status = "verified",
        profile_photo = "h.jpg"
    )

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EBomaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        listingDao = db.listingDao()
        bookingDao = db.bookingDao()
        paymentDao = db.paymentDao()
        groupMemberDao = db.groupMemberDao()

        userDao.insertUsers(listOf(testHost, testUser))

        val listing = Listing(
            listing_id = "lst_mpesa",
            host_id = testHost.uid,
            title = "M-Pesa Villa",
            location = "Kisumu",
            price_per_night = 10000.0,
            images = emptyList(),
            amenities = emptyList(),
            rating = 4.8,
            is_kyc_verified = true,
            is_physical_inspected = true
        )
        listingDao.insertListing(listing)

        val booking = Booking(
            booking_id = "bkg_mpesa",
            listing_id = listing.listing_id,
            group_id = "grp_mpesa",
            check_in_date = 1000L,
            check_out_date = 2000L,
            guest_count = 2,
            total_amount = 10000.0,
            status = "pending_payment"
        )
        bookingDao.insertBooking(booking)

        val member = GroupMember(
            group_id = "grp_mpesa",
            user_id = testUser.uid,
            split_amount = 5000.0,
            payment_status = "pending",
            role = "friend"
        )
        groupMemberDao.insertMember(member)

        mpesaService = MpesaPaymentService(fakeApiService, paymentDao, groupMemberDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun formatKenyanPhoneNumber_formatsCorrectly() {
        assertEquals("254712345678", mpesaService.formatKenyanPhoneNumber("0712345678"))
        assertEquals("254712345678", mpesaService.formatKenyanPhoneNumber("254712345678"))
        assertEquals("254712345678", mpesaService.formatKenyanPhoneNumber("+254 712 345 678"))
        assertEquals("254112345678", mpesaService.formatKenyanPhoneNumber("0112345678"))
    }

    @Test
    fun initiateStkPush_createsPendingPaymentInDatabase() = runBlocking {
        val result = mpesaService.initiateStkPush(
            bookingId = "bkg_mpesa",
            userId = testUser.uid,
            groupId = "grp_mpesa",
            amount = 5000.0,
            phoneNumber = "0712345678",
            bearerToken = "mock_token"
        )

        assertTrue(result.isSuccess)
        val payment = result.getOrNull()
        assertNotNull(payment)
        assertEquals("pending", payment?.status)
        assertEquals("mpesa", payment?.method)
        assertEquals("ws_CO_TEST_9999", payment?.stk_push_ref)

        // Verify stored in Room database
        val dbPayment = paymentDao.getPaymentById(payment!!.payment_id)
        assertNotNull(dbPayment)
        assertEquals("pending", dbPayment?.status)
    }

    @Test
    fun pollPaymentStatus_updatesPaymentAndGroupMemberToPaidOnSuccess() = runBlocking {
        val initResult = mpesaService.initiateStkPush(
            bookingId = "bkg_mpesa",
            userId = testUser.uid,
            groupId = "grp_mpesa",
            amount = 5000.0,
            phoneNumber = "0712345678",
            bearerToken = "mock_token"
        )
        val payment = initResult.getOrNull()!!

        // Poll with simulated success
        val states = mpesaService.pollPaymentStatus(
            payment = payment,
            groupId = "grp_mpesa",
            maxAttempts = 3,
            delayMillis = 10L,
            bearerToken = "mock_token",
            simulatedOutcome = "0"
        ).toList()

        assertTrue(states.any { it is PaymentPollingState.Polling })
        val successState = states.last() as PaymentPollingState.Success
        assertEquals("paid", successState.payment.status)

        // Verify database entity updated
        val updatedPayment = paymentDao.getPaymentById(payment.payment_id)
        assertEquals("paid", updatedPayment?.status)

        val updatedMember = groupMemberDao.getMember("grp_mpesa", testUser.uid)
        assertEquals("paid", updatedMember?.payment_status)
    }

    @Test
    fun pollPaymentStatus_updatesPaymentAndGroupMemberToFailedOnUserCancel() = runBlocking {
        val initResult = mpesaService.initiateStkPush(
            bookingId = "bkg_mpesa",
            userId = testUser.uid,
            groupId = "grp_mpesa",
            amount = 5000.0,
            phoneNumber = "0712345678",
            bearerToken = "mock_token"
        )
        val payment = initResult.getOrNull()!!

        // Poll with simulated cancellation (Code 1032)
        val states = mpesaService.pollPaymentStatus(
            payment = payment,
            groupId = "grp_mpesa",
            maxAttempts = 3,
            delayMillis = 10L,
            bearerToken = null,
            simulatedOutcome = "1032"
        ).toList()

        val failedState = states.last() as PaymentPollingState.Failed
        assertEquals("failed", failedState.payment.status)

        // Verify database entity updated
        val updatedPayment = paymentDao.getPaymentById(payment.payment_id)
        assertEquals("failed", updatedPayment?.status)

        val updatedMember = groupMemberDao.getMember("grp_mpesa", testUser.uid)
        assertEquals("failed", updatedMember?.payment_status)
    }
}
