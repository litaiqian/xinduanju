package top.ipla.drama.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drama_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_POINTS = stringPreferencesKey("points")
    }

    suspend fun saveLogin(token: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
        }
    }

    suspend fun getToken(): String {
        return context.dataStore.data.map { it[KEY_TOKEN] ?: "" }.first()
    }

    suspend fun getUsername(): String {
        return context.dataStore.data.map { it[KEY_USERNAME] ?: "" }.first()
    }

    suspend fun isLoggedIn(): Boolean {
        return getToken().isNotEmpty()
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USERNAME)
        }
    }
}
