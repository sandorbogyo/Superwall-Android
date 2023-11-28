package com.superwall.sdk.store.abstractions.transactions

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.superwall.sdk.models.serialization.DateSerializer
import com.superwall.sdk.models.serialization.UUIDSerializer
import com.superwall.sdk.models.serialization.jsonStringToDictionary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
class StoreTransaction(
    @Transient
    private val transaction: GoogleBillingPurchaseTransaction,
    @SerialName("config_request_id")
    val configRequestId: String,
    @SerialName("app_session_id")
    val appSessionId: String,
    @SerialName("trigger_session_id")
    val triggerSessionId: String?
) : StoreTransactionType {
    val id = UUID.randomUUID().toString()

    override val transactionDate: Date? get() = transaction.transactionDate
    override val originalTransactionIdentifier: String? get() = transaction.originalTransactionIdentifier
    override val state: StoreTransactionState get() = transaction.state
    override val storeTransactionId: String? get() = transaction.storeTransactionId
    override val payment: StorePayment? get() = transaction.payment
    @Serializable(with = DateSerializer::class)
    override val originalTransactionDate: Date? get() = transaction.originalTransactionDate
    override val webOrderLineItemID: String? get() = transaction.webOrderLineItemID
    override val appBundleId: String? get() = transaction.appBundleId
    override val subscriptionGroupId: String? get() = transaction.subscriptionGroupId
    override val isUpgraded: Boolean? get() = transaction.isUpgraded
    @Serializable(with = DateSerializer::class)
    override val expirationDate: Date? get() = transaction.expirationDate
    override val offerId: String? get() = transaction.offerId
    @Serializable(with = DateSerializer::class)
    override val revocationDate: Date? get() = transaction.revocationDate
    @Serializable(with = UUIDSerializer::class)
    override val appAccountToken: UUID? get() = transaction.appAccountToken

//    fun toDictionary(): Map<String, Any> {
//        val json = Json { encodeDefaults = true }
//        val jsonString = json.encodeToString(this)
//        val dictionary = jsonString.jsonStringToDictionary()
//        return dictionary
//    }

    // TODO: The above should be working, but is somehow serializing `transaction` as a nested type
    //  even though it's marked as `Transient` and `private`, and failing to serialize the
    //  overrides. Flattening manually for now.
    fun toDictionary(): Map<String, Any?> {
        val json = Json { encodeDefaults = true }
        val jsonString = json.encodeToString(this)
        val dictionary = jsonString.jsonStringToDictionary().toMutableMap()

        val transactionMap = dictionary["transaction"] as? Map<String, Any>
        transactionMap?.let {
            // Remove the 'transaction' entry and add all its contents to the top level
            dictionary.remove("transaction")
            dictionary.putAll(it)
        }

        return dictionary
    }

}


