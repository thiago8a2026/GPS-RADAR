package com.example.billing

import com.example.auth.AuthRepository
import com.example.auth.AuthState
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
import retrofit2.http.Path

/**
 * Solicitud de checkout enviada únicamente a nuestro backend propio.
 * El cliente Android nunca almacena ni envía llaves privadas de Nequi, PayU o Stripe.
 */
@JsonClass(generateAdapter = true)
data class CreateCheckoutRequestDto(
    val planId: String = "premium_monthly",
    val provider: String, // "NEQUI" | "PAYU" | "STRIPE"
    val currency: String, // "COP" para Nequi/PayU, "USD" para Stripe
    val nequiPhoneNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateCheckoutResponseDto(
    val referenceId: String,
    val provider: String,
    val checkoutUrl: String? = null,
    val nequiTransactionId: String? = null,
    val nequiMaskedPhone: String? = null,
    val stripeClientSecret: String? = null,
    val stripeEphemeralKey: String? = null,
    val stripeCustomerId: String? = null,
    val stripePublishableKey: String? = null,
    val expiresAtEpochMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class EntitlementDto(
    val tier: String,
    val status: String,
    val provider: String? = null,
    val subscriptionId: String? = null,
    val expiresAtEpochMs: Long? = null,
    val autoRenew: Boolean = false
)

/**
 * Contrato HTTP hacia el backend de GPS ANTHI.
 * Cada pasarela firma y valida webhooks exclusivamente en el servidor:
 * - Nequi: Push Notification / Conecta API -> webhook servidor -> actualiza estado ACTIVE.
 * - PayU: Firma MD5/SHA256 generada en backend -> WebCheckout HTTPS -> página de confirmación webhook.
 * - Stripe: Checkout Session / Subscription creada con STRIPE_SECRET_KEY en backend -> webhook invoice.paid.
 */
interface GpsAnthiBillingApi {

    @POST("v1/billing/checkout")
    suspend fun createCheckout(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateCheckoutRequestDto
    ): CreateCheckoutResponseDto

    @GET("v1/billing/entitlement")
    suspend fun getCurrentEntitlement(
        @Header("Authorization") bearerToken: String
    ): EntitlementDto

    @GET("v1/billing/transactions/{referenceId}/status")
    suspend fun verifyTransactionStatus(
        @Header("Authorization") bearerToken: String,
        @Path("referenceId") referenceId: String
    ): EntitlementDto
}

sealed interface CheckoutLaunchAction {
    data class OpenHttpsCheckout(
        val url: String,
        val provider: PaymentProvider,
        val referenceId: String
    ) : CheckoutLaunchAction

    data class AwaitNequiPushApproval(
        val transactionId: String,
        val maskedPhone: String,
        val referenceId: String,
        val expiresAtEpochMs: Long
    ) : CheckoutLaunchAction

    data class LaunchStripePaymentSheet(
        val referenceId: String,
        val paymentIntentClientSecret: String,
        val ephemeralKey: String,
        val customerId: String,
        val publishableKey: String
    ) : CheckoutLaunchAction
}

/**
 * Orquestador seguro de suscripciones mensuales para GPS ANTHI.
 */
class SubscriptionManager(
    private val billingApi: GpsAnthiBillingApi,
    private val authRepository: AuthRepository
) {
    private val _subscriptionState = MutableStateFlow(SubscriptionState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    suspend fun startMonthlyCheckout(
        provider: PaymentProvider,
        nequiPhoneNumber: String? = null
    ): Result<CheckoutLaunchAction> = withContext(Dispatchers.IO) {
        val token = requireBearerToken()
            ?: return@withContext Result.failure(IllegalStateException("Inicia sesión para suscribirte"))

        val sanitizedNequiPhone = if (provider == PaymentProvider.NEQUI) {
            val digits = nequiPhoneNumber?.filter(Char::isDigit).orEmpty()
            if (digits.length != 10 || !digits.startsWith("3")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Ingresa un número Nequi válido de 10 dígitos (Colombia)")
                )
            }
            digits
        } else {
            null
        }

        val currency = when (provider) {
            PaymentProvider.NEQUI, PaymentProvider.PAYU -> "COP"
            PaymentProvider.STRIPE -> "USD"
        }

        runCatching {
            val response = billingApi.createCheckout(
                bearerToken = token,
                request = CreateCheckoutRequestDto(
                    planId = "premium_monthly",
                    provider = provider.name,
                    currency = currency,
                    nequiPhoneNumber = sanitizedNequiPhone
                )
            )
            mapResponseToAction(provider, response)
        }
    }

    suspend fun refreshEntitlement(): Result<SubscriptionState> = withContext(Dispatchers.IO) {
        val token = requireBearerToken()
            ?: return@withContext Result.failure(IllegalStateException("Sesión no iniciada"))

        runCatching {
            val dto = billingApi.getCurrentEntitlement(token)
            dto.toDomain().also { _subscriptionState.value = it }
        }
    }

    suspend fun verifyPendingTransaction(referenceId: String): Result<SubscriptionState> = withContext(Dispatchers.IO) {
        val token = requireBearerToken()
            ?: return@withContext Result.failure(IllegalStateException("Sesión no iniciada"))

        runCatching {
            val dto = billingApi.verifyTransactionStatus(token, referenceId)
            dto.toDomain().also { _subscriptionState.value = it }
        }
    }

    fun resetToFree() {
        _subscriptionState.value = SubscriptionState()
    }

    private fun requireBearerToken(): String? {
        val state = authRepository.authState.value
        return (state as? AuthState.Authenticated)?.let { "Bearer ${it.accessToken}" }
    }

    private fun mapResponseToAction(
        provider: PaymentProvider,
        response: CreateCheckoutResponseDto
    ): CheckoutLaunchAction {
        return when (provider) {
            PaymentProvider.NEQUI -> {
                val txId = response.nequiTransactionId
                    ?: error("El servidor no devolvió transactionId de Nequi")
                _subscriptionState.value = _subscriptionState.value.copy(
                    status = SubscriptionStatus.PENDING_CONFIRMATION,
                    provider = PaymentProvider.NEQUI
                )
                CheckoutLaunchAction.AwaitNequiPushApproval(
                    transactionId = txId,
                    maskedPhone = response.nequiMaskedPhone ?: "***",
                    referenceId = response.referenceId,
                    expiresAtEpochMs = response.expiresAtEpochMs ?: (System.currentTimeMillis() + 300_000L)
                )
            }

            PaymentProvider.PAYU -> {
                val url = validateHttpsUrl(response.checkoutUrl)
                CheckoutLaunchAction.OpenHttpsCheckout(
                    url = url,
                    provider = PaymentProvider.PAYU,
                    referenceId = response.referenceId
                )
            }

            PaymentProvider.STRIPE -> {
                val secret = response.stripeClientSecret
                val ephKey = response.stripeEphemeralKey
                val custId = response.stripeCustomerId
                val pubKey = response.stripePublishableKey
                if (secret != null && ephKey != null && custId != null && pubKey != null) {
                    CheckoutLaunchAction.LaunchStripePaymentSheet(
                        referenceId = response.referenceId,
                        paymentIntentClientSecret = secret,
                        ephemeralKey = ephKey,
                        customerId = custId,
                        publishableKey = pubKey
                    )
                } else {
                    CheckoutLaunchAction.OpenHttpsCheckout(
                        url = validateHttpsUrl(response.checkoutUrl),
                        provider = PaymentProvider.STRIPE,
                        referenceId = response.referenceId
                    )
                }
            }
        }
    }

    private fun validateHttpsUrl(rawUrl: String?): String {
        val url = rawUrl?.trim().orEmpty()
        require(url.startsWith("https://", ignoreCase = true)) {
            "URL de pasarela inválida: solo se permiten enlaces HTTPS"
        }
        return url
    }

    private fun EntitlementDto.toDomain(): SubscriptionState {
        val parsedTier = PlanTier.entries.firstOrNull { it.name == tier } ?: PlanTier.FREE
        val parsedStatus = SubscriptionStatus.entries.firstOrNull { it.name == status }
            ?: SubscriptionStatus.INACTIVE
        val parsedProvider = provider?.let { p -> PaymentProvider.entries.firstOrNull { it.name == p } }
        return SubscriptionState(
            tier = parsedTier,
            status = parsedStatus,
            provider = parsedProvider,
            subscriptionId = subscriptionId,
            expiresAtEpochMs = expiresAtEpochMs,
            autoRenew = autoRenew
        )
    }
}
