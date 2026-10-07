package com.example.data.remote.api

import com.example.data.remote.model.MpesaAuthResponse
import com.example.data.remote.model.MpesaStkPushRequest
import com.example.data.remote.model.MpesaStkPushResponse
import com.example.data.remote.model.MpesaStkQueryRequest
import com.example.data.remote.model.MpesaStkQueryResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Safaricom Daraja M-Pesa APIs.
 */
interface MpesaApiService {

    @GET("oauth/v1/generate")
    suspend fun getAccessToken(
        @Header("Authorization") basicAuthHeader: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): Response<MpesaAuthResponse>

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun sendStkPush(
        @Header("Authorization") bearerTokenHeader: String,
        @Body request: MpesaStkPushRequest
    ): Response<MpesaStkPushResponse>

    @POST("mpesa/stkpushquery/v1/query")
    suspend fun queryStkStatus(
        @Header("Authorization") bearerTokenHeader: String,
        @Body request: MpesaStkQueryRequest
    ): Response<MpesaStkQueryResponse>
}
