package io.github.nastechresearch.nastech.data.api

import io.github.nastechresearch.nastech.data.model.Sponsor
import io.github.nastechresearch.nastech.utils.JsonInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

interface SponsorAPI {
    @GET("/sponsors")
    suspend fun getSponsors(): List<Sponsor>

    companion object {
        fun create(httpClient: OkHttpClient): SponsorAPI {
            return Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://sponsors.rikka-ai.com")
                .addConverterFactory(JsonInstant.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SponsorAPI::class.java)
        }
    }
}
