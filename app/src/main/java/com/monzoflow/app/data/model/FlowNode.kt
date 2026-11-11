package com.monzoflow.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class NodeCategory {
    TRIGGER,
    MONZO_ACTION,
    LOGIC,
    TRANSFORM,
    EXTERNAL,
    NOTIFICATION
}

enum class NodeType {
    // Dedicated Monzo Personal Triggers
    TRIGGER_WEBHOOK,              // All Monzo transactions (raw webhook)
    TRIGGER_MONEY_RECEIVED,       // Money Received / Inbound Transfer / Salary / Refunds
    TRIGGER_CARD_SPEND,           // Card Purchase / Contactless / Apple Pay / POS Spend
    TRIGGER_TRANSFER_OUT,         // Outbound Bank Transfer / Send Money
    TRIGGER_DIRECT_DEBIT,         // Direct Debit / Standing Order / Subscriptions
    TRIGGER_POT_MOVE,             // Savings Pot Deposit or Withdrawal
    TRIGGER_DECLINE,              // Declined Transaction (Insufficient funds, card frozen)
    TRIGGER_FOREIGN_CURRENCY,     // Foreign Currency Spend Abroad (EUR, USD, etc.)
    TRIGGER_LOW_BALANCE,          // Low Account Balance Alert (Threshold buffer)
    TRIGGER_SIMULATOR,            // Test trigger with preset / custom mock
    TRIGGER_POLL,                 // Periodic polling of balance / transactions
    TRIGGER_MANUAL,               // Manual 'Run Now' tap

    // Monzo Actions
    MONZO_PUSH_FEED,              // POST /feed (Rich in-app Monzo card)
    MONZO_ANNOTATE,               // PATCH /transactions/{id} (Notes & #tags)
    MONZO_POT_DEPOSIT,            // PUT /pots/{id}/deposit
    MONZO_POT_WITHDRAW,           // PUT /pots/{id}/withdraw
    MONZO_GET_BALANCE,            // GET /balance

    // Logic & Flow
    LOGIC_FILTER,                 // If amount > X, category == Y, merchant contains Z
    LOGIC_SPLIT,                  // Fan-out / Split into multiple branches
    LOGIC_DELAY,                  // Throttle / Sleep ms

    // Math & Transform
    TRANSFORM_ROUNDUP,            // Spare change round-up (e.g. £3.40 -> £0.60)
    TRANSFORM_PERCENTAGE,         // Calculate % (e.g. 15% tax/savings)
    TRANSFORM_TEMPLATE,           // String template interpolation

    // External & Notifications
    EXTERNAL_HTTP_REQUEST,        // Generic webhook (Telegram, Discord, Home Assistant)
    NOTIFY_ANDROID                // Native Android notification
}

@Serializable
data class NodePort(
    val id: String,
    val name: String,
    val isInput: Boolean
)

@Serializable
data class NodeConfig(
    // Transaction Type & Status Filters (Webhook & Logic Filters)
    val filterTransactionType: String = "ALL",      // ALL, DEBIT, CREDIT
    val filterTransactionStatus: String = "ALL",    // ALL, PENDING, SETTLED, DECLINED
    val filterCategory: String = "",
    val filterMerchant: String = "",
    val filterMinAmountPence: Long = 0L,
    val filterMaxAmountPence: Long = Long.MAX_VALUE,
    val filterComparison: String = "GREATER_THAN", // GREATER_THAN, LESS_THAN, EQUALS
    val filterCurrency: String = "ALL",             // ALL, GBP, EUR, USD, FOREIGN
    val filterDeclineReason: String = "",           // e.g. INSUFFICIENT_FUNDS, CARD_FROZEN

    // Monzo Feed Item parameters
    val feedTitleTemplate: String = "MonzoFlow Alert",
    val feedBodyTemplate: String = "Transaction of {{amount}} at {{merchant}} processed.",
    val feedImageUrl: String = "https://cdn.iconscout.com/icon/free/png-256/free-monzo-logo-icon-download-in-svg-png-gif-file-formats--brand-social-media-pack-logos-icons-2673839.png",
    val feedTargetUrl: String = "",
    val feedBackgroundColor: String = "#FF4D4D",

    // Monzo Annotation parameters
    val annotateNotesTemplate: String = "Automated by MonzoFlow",
    val annotateTags: String = "#monzoflow #auto",

    // Pot Action parameters
    val potId: String = "",
    val potName: String = "",
    val potAmountType: String = "EXACT", // EXACT, ROUNDUP_SPARE, PERCENTAGE
    val potExactAmountPence: Long = 100L,
    val potPercentage: Float = 10f,

    // Transform parameters
    val roundUpTargetPence: Int = 100, // Round to nearest £1 (100p), £5 (500p)
    val templateString: String = "Spent {{amount}} at {{merchant}}",

    // External HTTP Request parameters
    val httpUrl: String = "",
    val httpMethod: String = "POST",
    val httpHeaders: Map<String, String> = emptyMap(),
    val httpBodyTemplate: String = "{\"event\": \"monzo_transaction\", \"amount\": \"{{amount}}\", \"merchant\": \"{{merchant}}\"}",

    // Android Notification parameters
    val notifyTitle: String = "MonzoFlow Alert",
    val notifyMessage: String = "Processed transaction: {{amount}}"
)

@Entity(tableName = "flow_nodes")
@Serializable
data class FlowNode(
    @PrimaryKey
    val id: String,
    val workflowId: String,
    val type: NodeType,
    val category: NodeCategory,
    val title: String,
    val subtitle: String = "",
    val posX: Float,
    val posY: Float,
    val config: NodeConfig = NodeConfig(),
    val inputs: List<NodePort> = defaultInputs(type),
    val outputs: List<NodePort> = defaultOutputs(type),
    val enabled: Boolean = true
) {
    companion object {
        fun defaultInputs(type: NodeType): List<NodePort> {
            return when (type) {
                NodeType.TRIGGER_WEBHOOK,
                NodeType.TRIGGER_MONEY_RECEIVED,
                NodeType.TRIGGER_CARD_SPEND,
                NodeType.TRIGGER_TRANSFER_OUT,
                NodeType.TRIGGER_DIRECT_DEBIT,
                NodeType.TRIGGER_POT_MOVE,
                NodeType.TRIGGER_DECLINE,
                NodeType.TRIGGER_FOREIGN_CURRENCY,
                NodeType.TRIGGER_LOW_BALANCE,
                NodeType.TRIGGER_SIMULATOR,
                NodeType.TRIGGER_POLL,
                NodeType.TRIGGER_MANUAL -> emptyList() // Triggers have no inputs
                else -> listOf(NodePort("in_default", "In", isInput = true))
            }
        }

        fun defaultOutputs(type: NodeType): List<NodePort> {
            return when (type) {
                NodeType.LOGIC_FILTER -> listOf(
                    NodePort("out_true", "Match (True)", isInput = false),
                    NodePort("out_false", "No Match (False)", isInput = false)
                )
                else -> listOf(NodePort("out_default", "Next", isInput = false))
            }
        }

        fun create(
            type: NodeType,
            posX: Float,
            posY: Float,
            workflowId: String = "default_wf"
        ): FlowNode {
            val cat = when (type) {
                NodeType.TRIGGER_WEBHOOK,
                NodeType.TRIGGER_MONEY_RECEIVED,
                NodeType.TRIGGER_CARD_SPEND,
                NodeType.TRIGGER_TRANSFER_OUT,
                NodeType.TRIGGER_DIRECT_DEBIT,
                NodeType.TRIGGER_POT_MOVE,
                NodeType.TRIGGER_DECLINE,
                NodeType.TRIGGER_FOREIGN_CURRENCY,
                NodeType.TRIGGER_LOW_BALANCE,
                NodeType.TRIGGER_SIMULATOR,
                NodeType.TRIGGER_POLL,
                NodeType.TRIGGER_MANUAL -> NodeCategory.TRIGGER
                NodeType.MONZO_PUSH_FEED,
                NodeType.MONZO_ANNOTATE,
                NodeType.MONZO_POT_DEPOSIT,
                NodeType.MONZO_POT_WITHDRAW,
                NodeType.MONZO_GET_BALANCE -> NodeCategory.MONZO_ACTION
                NodeType.LOGIC_FILTER,
                NodeType.LOGIC_SPLIT,
                NodeType.LOGIC_DELAY -> NodeCategory.LOGIC
                NodeType.TRANSFORM_ROUNDUP,
                NodeType.TRANSFORM_PERCENTAGE,
                NodeType.TRANSFORM_TEMPLATE -> NodeCategory.TRANSFORM
                NodeType.EXTERNAL_HTTP_REQUEST -> NodeCategory.EXTERNAL
                NodeType.NOTIFY_ANDROID -> NodeCategory.NOTIFICATION
            }
            return FlowNode(
                id = "node_${java.util.UUID.randomUUID().toString().take(8)}",
                workflowId = workflowId,
                type = type,
                category = cat,
                title = type.name.replace("TRIGGER_", "").replace("MONZO_", "").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                posX = posX,
                posY = posY
            )
        }
    }
}
