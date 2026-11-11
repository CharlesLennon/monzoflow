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
    // Triggers
    TRIGGER_WEBHOOK,              // Monzo transaction.created webhook
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
    // Filter parameters
    val filterCategory: String = "",
    val filterMerchant: String = "",
    val filterMinAmountPence: Long = 0L,
    val filterMaxAmountPence: Long = Long.MAX_VALUE,
    val filterComparison: String = "GREATER_THAN", // GREATER_THAN, LESS_THAN, EQUALS

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
    }
}
