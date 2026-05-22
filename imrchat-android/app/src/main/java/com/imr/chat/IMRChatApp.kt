package com.imr.chat

import android.app.Application
import com.imr.chat.data.SettingsStore
import com.imr.chat.data.db.AppDatabase

class IMRChatApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
