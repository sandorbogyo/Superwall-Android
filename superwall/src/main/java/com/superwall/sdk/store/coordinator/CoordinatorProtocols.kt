package com.superwall.sdk.store.coordinator

import com.superwall.sdk.delegate.PurchaseResult
import com.superwall.sdk.delegate.RestorationResult
import com.superwall.sdk.store.abstractions.product.StoreProduct
import com.superwall.sdk.store.abstractions.transactions.StoreTransaction

interface TransactionChecker {
    // Gets and validates a transaction of a product, if the user isn't using a PurchaseController.
    suspend fun getAndValidateLatestTransaction(
        productId: String,
        hasPurchaseController: Boolean = true // Always true in Kotlin
    ): StoreTransaction?
}

interface ProductPurchaser {
    // Purchases a product and returns its result.
    suspend fun purchase(product: StoreProduct): PurchaseResult
}

interface ProductsFetcher {
    // Fetches a set of products from their identifiers.
    suspend fun products(
        identifiers: Set<String>,
        paywallName: String? = null
    ): Set<StoreProduct>

    // Gets a users' purchased products.
    suspend fun purchasedProducts(): Set<StoreTransaction>
}

interface TransactionRestorer {
    // Restores purchases.
    // Returns: A boolean indicating whether the restore request succeeded or failed.
    // This doesn't mean that the user is now subscribed, just that there were no errors
    // obtaining the restored transactions
    suspend fun restorePurchases(): RestorationResult
}
