package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data models for Safaricom Daraja M-Pesa API integration.
 */

@JsonClass(generateAdapter = true)
data class MpesaAuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: String
)

@JsonClass(generateAdapter = true)
data class MpesaStkPushRequest(
    @Json(name = "BusinessShortCode") val businessShortCode: String,
    @Json(name = "Password") val password: String,
    @Json(name = "Timestamp") val timestamp: String,
    @Json(name = "TransactionType") val transactionType: String = "CustomerPayBillOnline",
    @Json(name = "Amount") val amount: Long,
    @Json(name = "PartyA") val partyA: String, // Customer phone number, e.g. 254712345678
    @Json(name = "PartyB") val partyB: String, // Shortcode
    @Json(name = "PhoneNumber") val phoneNumber: String, // 254712345678
    @Json(name = "CallBackURL") val callBackUrl: String,
    @Json(name = "AccountReference") val accountReference: String,
    @Json(name = "TransactionDesc") val transactionDesc: String
)

@JsonClass(generateAdapter = true)
data class MpesaStkPushResponse(
    @Json(name = "MerchantRequestID") val merchantRequestId: String?,
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String?,
    @Json(name = "ResponseCode") val responseCode: String?,
    @Json(name = "ResponseDescription") val responseDescription: String?,
    @Json(name = "CustomerMessage") val customerMessage: String?
)

@JsonClass(generateAdapter = true)
data class MpesaStkQueryRequest(
    @Json(name = "BusinessShortCode") val businessShortCode: String,
    @Json(name = "Password") val password: String,
    @Json(name = "Timestamp") val timestamp: String,
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String
)

@JsonClass(generateAdapter = true)
data class MpesaStkQueryResponse(
    @Json(name = "ResponseCode") val responseCode: String?,
    @Json(name = "ResponseDescription") val responseDescription: String?,
    @Json(name = "MerchantRequestID") val merchantRequestId: String?,
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String?,
    @Json(name = "ResultCode") val resultCode: String?, // "0" = Success, "1032" = Cancelled
    @Json(name = "ResultDesc") val resultDesc: String?
)
