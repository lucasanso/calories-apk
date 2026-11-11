package com.calories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.AppSettings
import com.calories.domain.model.LibraryStatus
import com.calories.domain.repository.LibraryRepository
import com.calories.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val status: LibraryStatus) : LibraryState
    data class Failed(val message: String) : LibraryState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val libraryState: StateFlow<LibraryState> = _library.asStateFlow()

    init { loadLibrary() }

    fun loadLibrary() {
        _library.value = LibraryState.Loading
        viewModelScope.launch {
            _library.value = runCatching { LibraryState.Ready(library.ensureLibrary()) }
                .getOrElse { LibraryState.Failed(it.message ?: it.toString()) }
        }
    }
}
