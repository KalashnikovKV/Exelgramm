package com.example.exelgramm.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    val username = store.session
        .map { it.username }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun logout() {
        viewModelScope.launch { store.logout() }
    }
}
