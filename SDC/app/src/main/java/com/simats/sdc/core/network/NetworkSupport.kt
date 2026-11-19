package com.simats.sdc.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simats.sdc.core.datastore.SessionManager
import com.simats.sdc.data.dto.AuthTokenResponseDto
import com.simats.sdc.data.dto.RefreshTokenRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnlineNow(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        trySend(isOnlineNow())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnlineNow())
            }
        }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val session = runBlocking { sessionManager.getSessionSnapshot() }
        var request = chain.request()

        // Dynamically re-target base URL if user configured a custom backend host in Settings/Login
        val configuredUrl = session.baseUrl.toHttpUrlOrNull()
        if (configuredUrl != null) {
            val newUrl = request.url.newBuilder()
                .scheme(configuredUrl.scheme)
                .host(configuredUrl.host)
                .port(configuredUrl.port)
                .build()
            request = request.newBuilder().url(newUrl).build()
        }

        val builder = request.newBuilder()
            .header("ngrok-skip-browser-warning", "true")

        if (session.accessToken.isNotBlank() && !request.url.encodedPath.endsWith("/auth/login")) {
            builder.header("Authorization", "Bearer ${session.accessToken}")
        }
        return chain.proceed(builder.build())
    }
}

@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    private val gson: Gson,
) : Authenticator {
    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.contains("/auth/")) return null

        val session = runBlocking { sessionManager.getSessionSnapshot() }
        if (session.refreshToken.isBlank()) return null

        val refreshUrl = "${session.baseUrl.trimEnd('/')}/auth/refresh"
        val bodyJson = gson.toJson(RefreshTokenRequestDto(session.refreshToken))
        val refreshReq = Request.Builder()
            .url(refreshUrl)
            .header("ngrok-skip-browser-warning", "true")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val client = OkHttpClient()
            client.newCall(refreshReq).execute().use { refreshResp ->
                if (!refreshResp.isSuccessful) {
                    runBlocking { sessionManager.clearSession() }
                    return null
                }
                val respStr = refreshResp.body?.string() ?: return null
                val parsed = gson.fromJson(respStr, AuthTokenResponseDto::class.java)
                runBlocking {
                    sessionManager.updateTokens(parsed.accessToken, parsed.refreshToken)
                }
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${parsed.accessToken}")
                    .build()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

object ApiErrorParser {
    private val gson = Gson()

    fun <T> parseError(response: Response<T>): String {
        val code = response.code()
        val raw = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (!raw.isNullOrBlank()) {
            try {
                val obj = gson.fromJson(raw, JsonObject::class.java)
                if (obj.has("detail")) {
                    val detail = obj.get("detail")
                    return if (detail.isJsonPrimitive) detail.asString else detail.toString()
                }
            } catch (_: Exception) {
                // fallback below
            }
        }
        return when (code) {
            401 -> "Session expired or unauthorized. Please sign in again."
            403 -> "Access denied for your assigned role or scope."
            404 -> "Requested record was not found."
            409 -> "Duplicate record conflict."
            422 -> "Please verify all required fields and formats."
            423 -> "Account temporarily locked due to failed login attempts."
            else -> "Request failed (HTTP $code)."
        }
    }
}
