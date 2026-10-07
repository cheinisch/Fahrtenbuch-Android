package app.fahrtenbuch.mobile.data.api

import android.util.Log
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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object ApiFactory {
    private const val AUTH_LOG_TAG = "FahrtenbuchAuth"
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
        val refreshClient = clientBuilder(store).build()
        val refreshApi = retrofit(store.baseUrl, refreshClient)

        val authenticator = Authenticator { _: Route?, response: Response ->
            Log.w(
                AUTH_LOG_TAG,
                "Authenticator invoked for ${response.request.method} ${response.request.url.encodedPath}: " +
                    "response=${response.code}, " +
                    "requestHadAuthorization=${response.request.header("Authorization") != null}, " +
                    "accessTokenPresent=${!store.accessToken.isNullOrBlank()}, " +
                    "refreshTokenPresent=${!store.refreshToken.isNullOrBlank()}"
            )
            if (responseCount(response) >= 2) {
                Log.w(AUTH_LOG_TAG, "Authenticator stopped: retry limit reached")
                return@Authenticator null
            }

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

                val refreshToken = store.refreshToken ?: run {
                    Log.w(AUTH_LOG_TAG, "Authenticator stopped: no refresh token stored")
                    return@synchronized null
                }
                val refreshed = try {
                    runBlocking { refreshApi.refresh(RefreshRequest(refreshToken)) }
                } catch (error: HttpException) {
                    // Only destroy the local session when the refresh endpoint itself
                    // confirms that the refresh token is invalid. A transient/proxy
                    // 401/5xx must not silently log the user out.
                    Log.w(AUTH_LOG_TAG, "Token refresh failed with HTTP ${error.code()}")
                    if (error.code() == 401) store.clearTokens()
                    return@synchronized null
                } catch (error: Exception) {
                    Log.w(AUTH_LOG_TAG, "Token refresh failed: ${error.javaClass.simpleName}: ${error.message}")
                    return@synchronized null
                }

                Log.d(AUTH_LOG_TAG, "Token refresh succeeded; retrying request with refreshed access token")
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

        val client = clientBuilder(store)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder: Request.Builder = original.newBuilder()
                val accessToken = store.accessToken?.takeIf { it.isNotBlank() }
                val refreshTokenPresent = !store.refreshToken.isNullOrBlank()

                if (accessToken != null) {
                    builder.header("Authorization", "Bearer $accessToken")
                }
                builder.header("Accept", "application/json")

                val request = builder.build()
                val authorizationAttached = request.header("Authorization")
                    ?.startsWith("Bearer ") == true

                Log.d(
                    AUTH_LOG_TAG,
                    "Request ${request.method} ${request.url.encodedPath}: " +
                        "accessTokenPresent=${accessToken != null}, " +
                        "refreshTokenPresent=$refreshTokenPresent, " +
                        "authorizationAttached=$authorizationAttached"
                )

                val response = chain.proceed(request)

                if (response.code == 401) {
                    Log.w(
                        AUTH_LOG_TAG,
                        "HTTP 401 for ${request.method} ${request.url.encodedPath}: " +
                            "accessTokenPresent=${accessToken != null}, " +
                            "refreshTokenPresent=$refreshTokenPresent, " +
                            "authorizationAttached=$authorizationAttached"
                    )
                }

                response
            }
            .authenticator(authenticator)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        return retrofit(store.baseUrl, client)
    }

    private fun clientBuilder(store: SessionStore): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        if (store.allowInvalidCertificates && store.baseUrl.startsWith("https://", ignoreCase = true)) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
            Log.w(AUTH_LOG_TAG, "TLS certificate validation is DISABLED for the configured server")
        }
        return builder
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
