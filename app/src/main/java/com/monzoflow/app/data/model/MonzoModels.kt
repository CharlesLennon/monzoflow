package com.monzoflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MonzoAccountsResponse(
    val accounts: List<MonzoAccount> = emptyList()
)

@Serializable
data class MonzoAccount(
    val id: String,
    val description: String = "",
    val created: String = "",
    val closed: Boolean = false,
    val currency: String = "GBP",
    val type: String = "uk_retail" // uk_retail, uk_retail_joint, uk_business
)

@Serializable
data class MonzoBalance(
    val balance: Long = 0L,              // In minor units (pence)
    val total_balance: Long = 0L,        // Including pots
    val currency: String = "GBP",
    val spend_today: Long = 0L
) {
    val balanceFormatted: String
        get() = formatCurrency(balance, currency)

    val spendTodayFormatted: String
        get() = formatCurrency(spend_today, currency)

    val totalBalanceFormatted: String
        get() = formatCurrency(total_balance, currency)

    private fun formatCurrency(pence: Long, curr: String): String {
        val sign = if (pence < 0) "-" else ""
        val absPence = kotlin.math.abs(pence)
        val pounds = absPence / 100
        val remainingPence = absPence % 100
        val symbol = if (curr.equals("GBP", ignoreCase = true)) "£" else "$curr "
        return "%s%s%d.%02d".format(sign, symbol, pounds, remainingPence)
    }
}

@Serializable
data class MonzoPotsResponse(
    val pots: List<MonzoPot> = emptyList()
)

@Serializable
data class MonzoPot(
    val id: String,
    val name: String,
    val style: String = "",
    val balance: Long = 0L,              // In minor units (pence)
    val currency: String = "GBP",
    val deleted: Boolean = false,
    val goal_amount: Long? = null
) {
    val balanceFormatted: String
        get() {
            val pounds = balance / 100
            val pence = balance % 100
            val symbol = if (currency.equals("GBP", ignoreCase = true)) "£" else "$currency "
            return "%s%d.%02d".format(symbol, pounds, pence)
        }
}

@Serializable
data class MonzoTransactionsResponse(
    val transactions: List<MonzoTransaction> = emptyList()
)

@Serializable
data class MonzoTransaction(
    val id: String,
    val amount: Long,                    // Negative for spend, positive for income (in pence)
    val currency: String = "GBP",
    val description: String = "",
    val notes: String = "",
    val category: String = "general",
    val created: String = "",
    val settled: String? = null,
    val merchant: MonzoMerchant? = null,
    val decline_reason: String? = null,
    val is_load: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val account_id: String = ""
) {
    val isSpend: Boolean get() = amount < 0
    val isIncome: Boolean get() = amount > 0
    val amountAbs: Long get() = kotlin.math.abs(amount)

    val amountFormatted: String
        get() {
            val sign = if (amount < 0) "-£" else "+£"
            val pounds = amountAbs / 100
            val pence = amountAbs % 100
            return "%s%d.%02d".format(sign, pounds, pence)
        }

    val displayTitle: String
        get() = merchant?.name?.takeIf { it.isNotBlank() } ?: description.ifBlank { "Monzo Transaction" }
}

@Serializable
data class MonzoMerchant(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val emoji: String = "💳",
    val category: String = "general"
)

@Serializable
data class MonzoWebhookPayload(
    val type: String = "transaction.created",
    val data: MonzoTransaction
)

@Serializable
data class MonzoFeedItemRequest(
    val account_id: String,
    val type: String = "basic",
    val params: Map<String, String>
)
