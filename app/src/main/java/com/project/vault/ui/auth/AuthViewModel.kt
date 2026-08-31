package com.project.vault.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.repository.AuthException
import com.project.vault.repository.AuthRepository
import com.project.vault.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val message: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel() {

    private val _uiState = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val uiState: LiveData<AuthUiState> = _uiState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both username and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.login(username.trim(), password)
            result.fold(
                onSuccess = {
                    _uiState.value = AuthUiState.Success("Logged in successfully")
                },
                onFailure = { error ->
                    val message = when (error) {
                        is AuthException.InvalidCredentialsException -> error.message ?: "Invalid username or password"
                        is AuthException.ApiException -> error.message ?: "Authentication failed"
                        else -> error.localizedMessage ?: "Network error occurred"
                    }
                    _uiState.value = AuthUiState.Error(message)
                }
            )
        }
    }

    fun signup(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both username and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signup(username.trim(), password)
            result.fold(
                onSuccess = {
                    _uiState.value = AuthUiState.Success("Account created successfully")
                },
                onFailure = { error ->
                    val message = when (error) {
                        is AuthException.UserAlreadyExistsException -> error.message ?: "Username already exists"
                        is AuthException.ApiException -> error.message ?: "Sign up failed"
                        else -> error.localizedMessage ?: "Network error occurred"
                    }
                    _uiState.value = AuthUiState.Error(message)
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
