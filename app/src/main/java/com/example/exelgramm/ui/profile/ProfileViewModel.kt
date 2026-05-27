package com.example.exelgramm.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.data.local.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val store: SessionStore) : ViewModel() {

    val username = store.session
        .map { it.username }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun logout() {
        viewModelScope.launch { store.logout() }
    }

    class Factory(private val store: SessionStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(store) as T
    }
}
