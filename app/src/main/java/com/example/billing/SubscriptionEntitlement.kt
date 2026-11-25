package com.example.billing

enum class PlanTier {
    FREE,
    PREMIUM_MONTHLY
}

enum class SubscriptionStatus {
    INACTIVE,
    PENDING_CONFIRMATION,
    ACTIVE,
    PAST_DUE,
    EXPIRED,
    CANCELLED
}

enum class PaymentProvider {
    NEQUI,
    PAYU,
    STRIPE
}

enum class PremiumFeature {
    AUTO_MOVEMENT_WALK_DRIVE,
    UNLIMITED_FAVORITES,
    GEOFENCING_ALERTS,
    COORDINATE_FORWARDING
}

data class SubscriptionState(
    val tier: PlanTier = PlanTier.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.INACTIVE,
    val provider: PaymentProvider? = null,
    val subscriptionId: String? = null,
    val expiresAtEpochMs: Long? = null,
    val autoRenew: Boolean = false
) {
    fun isPremiumActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (tier != PlanTier.PREMIUM_MONTHLY) return false
        if (status != SubscriptionStatus.ACTIVE) return false
        return expiresAtEpochMs == null || expiresAtEpochMs > nowEpochMs
    }

    fun maxSavedFavorites(nowEpochMs: Long = System.currentTimeMillis()): Int {
        return if (isPremiumActive(nowEpochMs)) Int.MAX_VALUE else FREE_MAX_FAVORITES
    }

    companion object {
        const val FREE_MAX_FAVORITES = 3
    }
}

sealed interface FeatureAccessResult {
    data object Allowed : FeatureAccessResult
    data class Locked(
        val feature: PremiumFeature,
        val reason: String
    ) : FeatureAccessResult
}

/**
 * Control centralizado de acceso a capacidades FREE vs PREMIUM_MONTHLY.
 */
object FeatureGate {

    fun checkAccess(
        subscription: SubscriptionState,
        feature: PremiumFeature,
        nowEpochMs: Long = System.currentTimeMillis()
    ): FeatureAccessResult {
        if (subscription.isPremiumActive(nowEpochMs)) {
            return FeatureAccessResult.Allowed
        }

        val reason = when (feature) {
            PremiumFeature.AUTO_MOVEMENT_WALK_DRIVE ->
                "El movimiento automático (Walk / Drive) requiere plan Premium mensual."
            PremiumFeature.UNLIMITED_FAVORITES ->
                "El plan gratuito permite hasta ${SubscriptionState.FREE_MAX_FAVORITES} ubicaciones favoritas."
            PremiumFeature.GEOFENCING_ALERTS ->
                "Las alertas por geocercas están disponibles en el plan Premium mensual."
            PremiumFeature.COORDINATE_FORWARDING ->
                "El reenvío automático de coordenadas requiere plan Premium mensual."
        }
        return FeatureAccessResult.Locked(feature = feature, reason = reason)
    }

    fun canAddFavorite(
        subscription: SubscriptionState,
        currentFavoritesCount: Int,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        return currentFavoritesCount < subscription.maxSavedFavorites(nowEpochMs)
    }
}
