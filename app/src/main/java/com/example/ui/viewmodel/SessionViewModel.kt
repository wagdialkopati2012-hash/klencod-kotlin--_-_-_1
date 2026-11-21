package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.prefs.AppThemeStyle
import com.example.data.repository.AuthenticatedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
  val isInitializing: Boolean = true,
  val currentUser: AuthenticatedUser? = null,
  val rememberedUsername: String = "",
  val rememberChecked: Boolean = true,
  val isDefaultAdminActive: Boolean = false,
  val isLoggingIn: Boolean = false,
  val loginError: String? = null,
  val isDarkMode: Boolean = false,
  val themeStyle: AppThemeStyle = AppThemeStyle.LIQUID_ROYAL,
)

class SessionViewModel(private val container: AppContainer) : ViewModel() {

  private val _authState = MutableStateFlow(SessionUiState())

  val uiState: StateFlow<SessionUiState> =
    combine(
        _authState,
        container.preferences.isDarkMode,
        container.preferences.themeStyle,
      ) { state, dark, style ->
        state.copy(isDarkMode = dark, themeStyle = style)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SessionUiState(),
      )

  init {
    viewModelScope.launch {
      container.authRepository.ensureInitialAdmin()
      val restoredUser = container.authRepository.restoreActiveSession()
      val remembered = container.preferences.savedUsername.first()
      val rememberFlag = container.preferences.rememberUsername.first()
      val defaultAdminActive = container.authRepository.isDefaultAdminCredentialsActive()

      _authState.update {
        it.copy(
          isInitializing = false,
          currentUser = restoredUser,
          rememberedUsername = remembered,
          rememberChecked = rememberFlag,
          isDefaultAdminActive = defaultAdminActive,
        )
      }

      // Keep active user synchronized with permission/status updates from DB
      launch {
        container.authRepository.allUsers.collect { users ->
          val activeId = _authState.value.currentUser?.entity?.id ?: return@collect
          val latest = users.firstOrNull { it.id == activeId }
          if (latest == null || !latest.isActive) {
            container.preferences.setLoggedInUserId(null)
            _authState.update { it.copy(currentUser = null) }
          } else {
            val authUser =
              com.example.data.repository.AuthenticatedUser(
                entity = latest,
                role = com.example.core.UserRole.fromString(latest.role),
                permissions =
                  com.example.core.UserPermissions.fromJson(
                    latest.permissionsJson,
                    com.example.core.UserRole.fromString(latest.role),
                  ),
              )
            _authState.update { it.copy(currentUser = authUser) }
          }
        }
      }
    }
  }

  fun login(username: String, password: String, remember: Boolean) {
    if (_authState.value.isLoggingIn) return
    viewModelScope.launch {
      _authState.update { it.copy(isLoggingIn = true, loginError = null) }
      val result = container.authRepository.login(username, password, remember)
      result
        .onSuccess { authUser ->
          val defaultAdminActive = container.authRepository.isDefaultAdminCredentialsActive()
          _authState.update {
            it.copy(
              isLoggingIn = false,
              currentUser = authUser,
              loginError = null,
              rememberedUsername = if (remember) username.trim() else "",
              rememberChecked = remember,
              isDefaultAdminActive = defaultAdminActive,
            )
          }
        }
        .onFailure { err ->
          _authState.update {
            it.copy(
              isLoggingIn = false,
              loginError = err.message ?: "تعذر تسجيل الدخول",
            )
          }
        }
    }
  }

  fun clearLoginError() {
    _authState.update { it.copy(loginError = null) }
  }

  fun logout() {
    val user = _authState.value.currentUser ?: return
    viewModelScope.launch {
      container.authRepository.logout(user)
      _authState.update { it.copy(currentUser = null, loginError = null) }
    }
  }

  fun setDarkMode(enabled: Boolean) {
    viewModelScope.launch { container.preferences.setDarkMode(enabled) }
  }

  fun setThemeStyle(style: AppThemeStyle) {
    viewModelScope.launch { container.preferences.setThemeStyle(style) }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      SessionViewModel(container) as T
  }
}
