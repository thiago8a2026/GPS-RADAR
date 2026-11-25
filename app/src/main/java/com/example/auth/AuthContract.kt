package com.example.auth

import kotlinx.coroutines.flow.StateFlow

enum class AuthProvider {
    EMAIL_PASSWORD,
    GOOGLE_CREDENTIAL_MANAGER
}

data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String?,
    val provider: AuthProvider = AuthProvider.EMAIL_PASSWORD,
    val boundDeviceId: String? = null
)

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(
        val user: AuthUser,
        val accessToken: String
    ) : AuthState
    data class Error(val message: String) : AuthState
}

/**
 * Contrato de autenticación de usuarios desacoplado de la capa UI.
 * La verificación de credenciales, hash de contraseñas y firma de JWT ocurren exclusivamente en el backend.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun signInWithEmail(email: String, password: String, deviceId: String): Result<AuthUser>
    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
        deviceId: String
    ): Result<AuthUser>

    suspend fun signInWithGoogleIdToken(idToken: String, deviceId: String): Result<AuthUser>
    suspend fun refreshSession(): Result<AuthUser>
    suspend fun signOut()
}

object AuthInputValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Ingresa un correo electrónico"
            !EMAIL_REGEX.matches(trimmed) -> "Correo electrónico inválido"
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            else -> null
        }
    }
}
