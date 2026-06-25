package com.example.exelgramm.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.data.local.AppPrefsStore
import com.example.exelgramm.data.local.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val store: SessionStore,
    private val appPrefs: AppPrefsStore,
) : ViewModel() {

    val username = store.authSession
        .map { it.username }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val createdAt = store.authSession
        .map { it.createdAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val isDarkTheme: Boolean
        get() = appPrefs.themeMode == AppCompatDelegate.MODE_NIGHT_YES

    fun setTheme(dark: Boolean) {
        val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        appPrefs.themeMode = mode
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun logout() {
        viewModelScope.launch { store.logout() }
    }
}
