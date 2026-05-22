package com.imr.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val name: String = "",
    val host: String = "",
    val port: Int = 18790,
    val token: String = "",
    val useWss: Boolean = false
)

data class AppSettings(
    val activeServerIndex: Int = 0,
    val servers: List<ServerConfig> = emptyList(),
    val darkMode: String = "system",  // "light", "dark", "system"
    val clientId: String = ""  // persistent device ID for session continuity
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val ACTIVE_SERVER = intPreferencesKey("active_server")
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val SERVERS_JSON = stringPreferencesKey("servers_json")
        private val CLIENT_ID = stringPreferencesKey("client_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val serversJson = prefs[SERVERS_JSON] ?: "[]"
        val servers = try {
            val arr = com.google.gson.JsonParser.parseString(serversJson).asJsonArray
            arr.map { elem ->
                val obj = elem.asJsonObject
                ServerConfig(
                    name = obj.get("name")?.asString ?: "",
                    host = obj.get("host")?.asString ?: "",
                    port = obj.get("port")?.asInt ?: 18790,
                    token = obj.get("token")?.asString ?: "",
                    useWss = obj.get("useWss")?.asBoolean ?: false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        AppSettings(
            activeServerIndex = prefs[ACTIVE_SERVER] ?: 0,
            servers = servers.ifEmpty {
                listOf(ServerConfig(name = "服务器", host = "127.0.0.1", port = 18790, token = "p2p2025"))
            },
            darkMode = prefs[DARK_MODE] ?: "system",
            clientId = prefs[CLIENT_ID] ?: ""
        )
    }

    suspend fun getOrCreateClientId(): String {
        val current = context.dataStore.data.first()[CLIENT_ID] ?: ""
        if (current.isNotBlank()) return current
        val newId = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[CLIENT_ID] = newId }
        return newId
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_SERVER] = settings.activeServerIndex
            prefs[DARK_MODE] = settings.darkMode
            if (settings.clientId.isNotBlank()) prefs[CLIENT_ID] = settings.clientId

            val gson = com.google.gson.Gson()
            prefs[SERVERS_JSON] = gson.toJson(settings.servers.map { server ->
                mapOf(
                    "name" to server.name,
                    "host" to server.host,
                    "port" to server.port,
                    "token" to server.token,
                    "useWss" to server.useWss
                )
            })
        }
    }
}
