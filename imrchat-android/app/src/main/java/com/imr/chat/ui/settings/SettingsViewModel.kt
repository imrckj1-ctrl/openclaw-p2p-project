package com.imr.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imr.chat.data.AppSettings
import com.imr.chat.data.ServerConfig
import com.imr.chat.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val settings = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    fun saveServer(index: Int, server: ServerConfig) {
        viewModelScope.launch {
            val current = settings.value.servers.toMutableList()
            if (index in current.indices) {
                current[index] = server
            } else {
                current.add(server)
            }
            settingsStore.saveSettings(settings.value.copy(servers = current))
        }
    }

    fun addServer(server: ServerConfig) {
        viewModelScope.launch {
            val current = settings.value.servers.toMutableList()
            current.add(server)
            settingsStore.saveSettings(settings.value.copy(servers = current))
        }
    }

    fun removeServer(index: Int) {
        viewModelScope.launch {
            val current = settings.value.servers.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                settingsStore.saveSettings(settings.value.copy(servers = current))
            }
        }
    }

    fun setActiveServer(index: Int) {
        viewModelScope.launch {
            settingsStore.saveSettings(settings.value.copy(activeServerIndex = index))
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            settingsStore.saveSettings(settings.value.copy(darkMode = mode))
        }
    }

    fun clearChatHistory(onDone: () -> Unit) {
        viewModelScope.launch {
            onDone()
        }
    }

    class Factory(private val settingsStore: SettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsStore) as T
        }
    }
}
