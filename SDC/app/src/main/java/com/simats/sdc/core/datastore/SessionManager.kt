package com.simats.sdc.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simats.sdc.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.oralCareDataStore: DataStore<Preferences> by preferencesDataStore(name = "oralcare_session")

data class UserSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: Int = 0,
    val userUid: String = "",
    val username: String = "",
    val fullName: String = "",
    val role: String = "",
    val collegeId: Int? = null,
    val centerId: Int? = null,
    val mustChangePassword: Boolean = false,
    val darkMode: Boolean = false,
    val baseUrl: String = BuildConfig.DEFAULT_BASE_URL,
) {
    val isAuthenticated: Boolean get() = accessToken.isNotBlank() && role.isNotBlank()
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = intPreferencesKey("user_id")
        val USER_UID = stringPreferencesKey("user_uid")
        val USERNAME = stringPreferencesKey("username")
        val FULL_NAME = stringPreferencesKey("full_name")
        val ROLE = stringPreferencesKey("role")
        val COLLEGE_ID = intPreferencesKey("college_id")
        val CENTER_ID = intPreferencesKey("center_id")
        val MUST_CHANGE_PASSWORD = booleanPreferencesKey("must_change_password")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    val sessionFlow: Flow<UserSession> = context.oralCareDataStore.data.map { prefs ->
        UserSession(
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
            userId = prefs[Keys.USER_ID] ?: 0,
            userUid = prefs[Keys.USER_UID] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            fullName = prefs[Keys.FULL_NAME] ?: "",
            role = prefs[Keys.ROLE] ?: "",
            collegeId = prefs[Keys.COLLEGE_ID],
            centerId = prefs[Keys.CENTER_ID],
            mustChangePassword = prefs[Keys.MUST_CHANGE_PASSWORD] ?: false,
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            baseUrl = prefs[Keys.BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL,
        )
    }

    suspend fun getSessionSnapshot(): UserSession = sessionFlow.first()

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: Int,
        userUid: String,
        username: String,
        fullName: String,
        role: String,
        collegeId: Int?,
        centerId: Int?,
        mustChangePassword: Boolean,
    ) {
        context.oralCareDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_UID] = userUid
            prefs[Keys.USERNAME] = username
            prefs[Keys.FULL_NAME] = fullName
            prefs[Keys.ROLE] = role
            if (collegeId != null) prefs[Keys.COLLEGE_ID] = collegeId else prefs.remove(Keys.COLLEGE_ID)
            if (centerId != null) prefs[Keys.CENTER_ID] = centerId else prefs.remove(Keys.CENTER_ID)
            prefs[Keys.MUST_CHANGE_PASSWORD] = mustChangePassword
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.oralCareDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearPasswordChangeFlag() {
        context.oralCareDataStore.edit { prefs ->
            prefs[Keys.MUST_CHANGE_PASSWORD] = false
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.oralCareDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.oralCareDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = normalized
        }
    }

    suspend fun clearSession() {
        context.oralCareDataStore.edit { prefs ->
            val darkMode = prefs[Keys.DARK_MODE] ?: false
            val baseUrl = prefs[Keys.BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL
            prefs.clear()
            prefs[Keys.DARK_MODE] = darkMode
            prefs[Keys.BASE_URL] = baseUrl
        }
    }
}
