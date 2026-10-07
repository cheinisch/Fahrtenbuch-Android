package app.fahrtenbuch.mobile.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import app.fahrtenbuch.mobile.data.model.RefreshRequest
import app.fahrtenbuch.mobile.data.model.ApiError
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiFactory {
    private val refreshLock = Any()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val apiErrorAdapter = moshi.adapter(ApiError::class.java)

    fun readableError(error: Throwable): String {
        if (error !is HttpException) return error.message ?: error.javaClass.simpleName
        val parsed = runCatching {
            error.response()?.errorBody()?.string()?.let(apiErrorAdapter::fromJson)
        }.getOrNull()
        val code = parsed?.code
        val message = parsed?.message
        return when (code) {
            "UNAUTHORIZED" -> "Anmeldung erforderlich (UNAUTHORIZED)."
            "INVALID_ACCESS_TOKEN" -> "Access-Token ungültig oder abgelaufen (INVALID_ACCESS_TOKEN)."
            "SESSION_INVALID" -> "Sitzung ungültig oder abgelaufen (SESSION_INVALID)."
            "INVALID_REFRESH_TOKEN" -> "Refresh-Token ungültig oder abgelaufen (INVALID_REFRESH_TOKEN)."
            else -> listOfNotNull(
                message?.takeIf { it.isNotBlank() },
                code?.takeIf { it.isNotBlank() }?.let { "($it)" },
                "HTTP ${error.code()}"
            ).joinToString(" ").ifBlank { "HTTP ${error.code()}" }
        }
    }

    fun create(store: SessionStore): FahrtenbuchApi {
        val refreshClient = OkHttpClient.Builder().build()
        val refreshApi = retrofit(store.baseUrl, refreshClient)

        val authenticator = Authenticator { _: Route?, response: Response ->
            if (responseCount(response) >= 2) return@Authenticator null

            synchronized(refreshLock) {
                val currentAccessToken = store.accessToken
                val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                // Another request may already have rotated the refresh token while
                // this request was waiting for the lock. Reuse the newer access token.
                if (!currentAccessToken.isNullOrBlank() && requestToken != currentAccessToken) {
                    return@synchronized response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                val refreshToken = store.refreshToken ?: return@synchronized null
                val refreshed = try {
                    runBlocking { refreshApi.refresh(RefreshRequest(refreshToken)) }
                } catch (error: HttpException) {
                    // Only destroy the local session when the refresh endpoint itself
                    // confirms that the refresh token is invalid. A transient/proxy
                    // 401/5xx must not silently log the user out.
                    if (error.code() == 401) store.clearTokens()
                    return@synchronized null
                } catch (_: Exception) {
                    return@synchronized null
                }

                val nextRefreshToken = refreshed.refreshToken ?: refreshToken
                if (!store.completeLogin(refreshed.accessToken, nextRefreshToken)) {
                    store.clearTokens()
                    return@synchronized null
                }

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.accessToken}")
                    .build()
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder: Request.Builder = chain.request().newBuilder()
                store.accessToken?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
                builder.header("Accept", "application/json")
                chain.proceed(builder.build())
            }
            .authenticator(authenticator)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        return retrofit(store.baseUrl, client)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): FahrtenbuchApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build().create(FahrtenbuchApi::class.java)

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
