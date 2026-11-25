package com.example.auth

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    val email: String,
    val password: String,
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class GoogleSignInRequestDto(
    val idToken: String,
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class AuthSessionResponseDto(
    val userId: String,
    val email: String,
    val displayName: String? = null,
    val provider: String = "EMAIL_PASSWORD",
    val boundDeviceId: String? = null,
    val accessToken: String
)

interface GpsAnthiAuthApi {
    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthSessionResponseDto

    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthSessionResponseDto

    @POST("v1/auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequestDto): AuthSessionResponseDto

    @GET("v1/auth/me")
    suspend fun getProfile(@Header("Authorization") bearerToken: String): AuthSessionResponseDto

    @POST("v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearerToken: String)
}

/**
 * Implementación concreta de AuthRepository respaldada por el backend HTTPS de GPS ANTHI.
 * Mantiene el token de sesión en memoria y valida credenciales únicamente en servidor.
 */
class RemoteAuthRepository(
    private val authApi: GpsAnthiAuthApi
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun signInWithEmail(
        email: String,
        password: String,
        deviceId: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        AuthInputValidator.validateEmail(email)?.let { err ->
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        AuthInputValidator.validatePassword(password)?.let { err ->
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _authState.value = AuthState.Authenticating
        runCatching {
            val res = authApi.login(
                LoginRequestDto(
                    email = email.trim(),
                    password = password,
                    deviceId = deviceId
                )
            )
            applyAuthenticatedSession(res)
        }.onFailure { error ->
            _authState.value = AuthState.Error(error.localizedMessage ?: "No se pudo iniciar sesión")
        }
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
        deviceId: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        AuthInputValidator.validateEmail(email)?.let { err ->
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        AuthInputValidator.validatePassword(password)?.let { err ->
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            val err = "Ingresa un nombre válido (mínimo 2 caracteres)"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _authState.value = AuthState.Authenticating
        runCatching {
            val res = authApi.register(
                RegisterRequestDto(
                    email = email.trim(),
                    password = password,
                    displayName = cleanName,
                    deviceId = deviceId
                )
            )
            applyAuthenticatedSession(res)
        }.onFailure { error ->
            _authState.value = AuthState.Error(error.localizedMessage ?: "No se pudo completar el registro")
        }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        deviceId: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        if (idToken.isBlank()) {
            val err = "Token de Google inválido"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _authState.value = AuthState.Authenticating
        runCatching {
            val res = authApi.signInWithGoogle(
                GoogleSignInRequestDto(
                    idToken = idToken,
                    deviceId = deviceId
                )
            )
            applyAuthenticatedSession(res)
        }.onFailure { error ->
            _authState.value = AuthState.Error(error.localizedMessage ?: "Error autenticando con Google")
        }
    }

    override suspend fun refreshSession(): Result<AuthUser> = withContext(Dispatchers.IO) {
        val current = _authState.value as? AuthState.Authenticated
            ?: return@withContext Result.failure(IllegalStateException("No hay sesión activa"))

        runCatching {
            val res = authApi.getProfile("Bearer ${current.accessToken}")
            applyAuthenticatedSession(res)
        }.onFailure {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            val current = _authState.value as? AuthState.Authenticated
            if (current != null) {
                runCatching { authApi.logout("Bearer ${current.accessToken}") }
            }
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun applyAuthenticatedSession(dto: AuthSessionResponseDto): AuthUser {
        val provider = AuthProvider.entries.firstOrNull { it.name == dto.provider }
            ?: AuthProvider.EMAIL_PASSWORD
        val user = AuthUser(
            userId = dto.userId,
            email = dto.email,
            displayName = dto.displayName,
            provider = provider,
            boundDeviceId = dto.boundDeviceId
        )
        _authState.value = AuthState.Authenticated(
            user = user,
            accessToken = dto.accessToken
        )
        return user
    }
}
