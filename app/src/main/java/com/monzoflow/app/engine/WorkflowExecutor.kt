package com.monzoflow.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.monzoflow.app.data.local.AppDatabase
import com.monzoflow.app.data.model.*
import com.monzoflow.app.data.secure.SecureCredentialManager
import com.monzoflow.app.monzo.MonzoApiClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import timber.log.Timber
import java.util.UUID

class WorkflowExecutor(
    private val context: Context,
    private val database: AppDatabase,
    private val credentialManager: SecureCredentialManager,
    private val monzoClient: MonzoApiClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
    private val genericHttpClient = HttpClient(CIO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MonzoFlow Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automated alerts triggered by MonzoFlow workflows"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Executes all active workflows that match an incoming Monzo transaction event.
     */
    suspend fun handleIncomingTransaction(
        transaction: MonzoTransaction,
        source: String = "Webhook"
    ): List<ExecutionLog> = withContext(Dispatchers.IO) {
        val activeWorkflows = database.workflowDao().getActiveWorkflows()
        val logs = mutableListOf<ExecutionLog>()

        for (wf in activeWorkflows) {
            val nodes = database.workflowDao().getNodesSnapshot(wf.id)
            val hasMatchingTrigger = nodes.any { node ->
                matchesTrigger(node, transaction, source)
            }

            if (hasMatchingTrigger) {
                val log = executeWorkflow(wf, transaction, source)
                logs.add(log)
            }
        }
        logs
    }

    /**
     * Executes a single workflow with a transaction context.
     */
    suspend fun executeWorkflow(
        workflow: Workflow,
        transaction: MonzoTransaction,
        source: String = "Manual"
    ): ExecutionLog = withContext(Dispatchers.IO) {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val steps = mutableListOf<NodeExecutionStep>()
        var overallStatus = ExecutionStatus.SUCCESS
        var errorSummary: String? = null

        val nodes = database.workflowDao().getNodesSnapshot(workflow.id).associateBy { it.id }
        val connections = database.workflowDao().getConnectionsSnapshot(workflow.id)

        // Initial context data dictionary
        val contextData = mutableMapOf<String, Any>(
            "amount" to transaction.amountFormatted,
            "amount_pence" to transaction.amount,
            "amount_abs_pence" to transaction.amountAbs,
            "merchant" to transaction.displayTitle,
            "category" to transaction.category,
            "notes" to transaction.notes,
            "account_id" to transaction.account_id,
            "transaction_id" to transaction.id,
            "is_spend" to transaction.isSpend,
            "is_income" to transaction.isIncome
        )

        // Find starting trigger node
        val startNode = nodes.values.firstOrNull { node ->
            matchesTrigger(node, transaction, source)
        } ?: nodes.values.firstOrNull { it.category == NodeCategory.TRIGGER }

        if (startNode == null) {
            val finishedAt = System.currentTimeMillis()
            val failedLog = ExecutionLog(
                id = runId,
                workflowId = workflow.id,
                workflowName = workflow.name,
                triggerSource = source,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMs = finishedAt - startedAt,
                errorSummary = "No matching trigger node found in workflow",
                triggerPayloadJson = json.encodeToString(transaction)
            )
            database.executionLogDao().insertLog(failedLog)
            return@withContext failedLog
        }

        // Execute Trigger Step
        val triggerStep = NodeExecutionStep(
            nodeId = startNode.id,
            nodeTitle = startNode.title,
            nodeType = startNode.type,
            status = ExecutionStatus.SUCCESS,
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            durationMs = System.currentTimeMillis() - startedAt,
            inputJson = "{}",
            outputJson = json.encodeToString(transaction)
        )
        steps.add(triggerStep)

        // BFS / Topological Traversal Queue: Pair(Node, incomingPortId)
        val queue = ArrayDeque<Pair<FlowNode, String>>()

        // Find initial targets connected to trigger output
        val initialConns = connections.filter { it.fromNodeId == startNode.id }
        for (conn in initialConns) {
            val targetNode = nodes[conn.toNodeId]
            if (targetNode != null && targetNode.enabled) {
                queue.add(Pair(targetNode, conn.toPortId))
            }
        }

        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val (currentNode, portId) = queue.removeFirst()
            val stepStart = System.currentTimeMillis()
            var stepStatus = ExecutionStatus.SUCCESS
            var stepError: String? = null
            var nodeOutputJson = "{}"

            val inputJsonSnapshot = json.encodeToString(contextData.mapValues { it.value.toString() })

            try {
                when (currentNode.type) {
                    NodeType.LOGIC_FILTER -> {
                        val passed = evaluateFilter(currentNode.config, transaction, contextData)
                        contextData["filter_result"] = passed
                        nodeOutputJson = "{\"filter_matched\": $passed}"

                        // Route to out_true or out_false
                        val targetPort = if (passed) "out_true" else "out_false"
                        val matchingConns = connections.filter { it.fromNodeId == currentNode.id && (it.fromPortId == targetPort || it.fromPortId == "out_default") }
                        for (c in matchingConns) {
                            val nextNode = nodes[c.toNodeId]
                            if (nextNode != null && nextNode.enabled) {
                                queue.add(Pair(nextNode, c.toPortId))
                            }
                        }
                    }

                    NodeType.TRANSFORM_ROUNDUP -> {
                        val spareChangePence = calculateRoundUp(transaction.amountAbs, currentNode.config.roundUpTargetPence)
                        contextData["roundup_pence"] = spareChangePence
                        contextData["roundup_formatted"] = "£%d.%02d".format(spareChangePence / 100, spareChangePence % 100)
                        nodeOutputJson = "{\"roundup_pence\": $spareChangePence, \"roundup_formatted\": \"${contextData["roundup_formatted"]}\"}"

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.TRANSFORM_PERCENTAGE -> {
                        val pct = currentNode.config.potPercentage
                        val calculated = (transaction.amountAbs * (pct / 100f)).toLong()
                        contextData["percentage_pence"] = calculated
                        contextData["percentage_formatted"] = "£%d.%02d".format(calculated / 100, calculated % 100)
                        nodeOutputJson = "{\"percentage\": $pct, \"calculated_pence\": $calculated}"

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.TRANSFORM_TEMPLATE -> {
                        val interpolated = interpolate(currentNode.config.templateString, contextData)
                        contextData["interpolated_text"] = interpolated
                        nodeOutputJson = "{\"text\": \"$interpolated\"}"

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.MONZO_PUSH_FEED -> {
                        val targetAccountId = transaction.account_id.ifBlank { credentialManager.selectedAccountId }
                        val title = interpolate(currentNode.config.feedTitleTemplate, contextData)
                        val body = interpolate(currentNode.config.feedBodyTemplate, contextData)

                        if (credentialManager.isConfigured && targetAccountId.isNotBlank()) {
                            val res = monzoClient.pushFeedItem(
                                accountId = targetAccountId,
                                title = title,
                                body = body,
                                imageUrl = currentNode.config.feedImageUrl,
                                targetUrl = currentNode.config.feedTargetUrl,
                                backgroundColor = currentNode.config.feedBackgroundColor
                            )
                            if (res.isFailure) {
                                throw res.exceptionOrNull() ?: Exception("Monzo feed push rejected")
                            }
                            nodeOutputJson = "{\"success\": true, \"account_id\": \"$targetAccountId\", \"title\": \"$title\"}"
                        } else {
                            nodeOutputJson = "{\"simulated\": true, \"title\": \"$title\", \"body\": \"$body\"}"
                        }

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.MONZO_ANNOTATE -> {
                        val notes = interpolate(currentNode.config.annotateNotesTemplate, contextData)
                        val tags = currentNode.config.annotateTags
                        val metadata = mutableMapOf<String, String>()
                        if (tags.isNotBlank()) {
                            metadata["tags"] = tags
                        }

                        if (credentialManager.isConfigured && transaction.id.isNotBlank() && !transaction.id.startsWith("sim_")) {
                            val res = monzoClient.annotateTransaction(transaction.id, notes, metadata)
                            if (res.isFailure) {
                                throw res.exceptionOrNull() ?: Exception("Monzo annotation failed")
                            }
                            nodeOutputJson = "{\"annotated\": true, \"transaction_id\": \"${transaction.id}\", \"notes\": \"$notes\"}"
                        } else {
                            nodeOutputJson = "{\"simulated\": true, \"notes\": \"$notes\", \"tags\": \"$tags\"}"
                        }

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.MONZO_POT_DEPOSIT -> {
                        val potId = currentNode.config.potId
                        val sourceAccount = transaction.account_id.ifBlank { credentialManager.selectedAccountId }
                        val amountToDeposit = when (currentNode.config.potAmountType) {
                            "ROUNDUP_SPARE" -> (contextData["roundup_pence"] as? Long) ?: calculateRoundUp(transaction.amountAbs, 100)
                            "PERCENTAGE" -> (contextData["percentage_pence"] as? Long) ?: (transaction.amountAbs * (currentNode.config.potPercentage / 100f)).toLong()
                            else -> currentNode.config.potExactAmountPence
                        }

                        if (credentialManager.isConfigured && potId.isNotBlank() && sourceAccount.isNotBlank() && amountToDeposit > 0) {
                            val res = monzoClient.depositToPot(potId, sourceAccount, amountToDeposit)
                            if (res.isFailure) {
                                throw res.exceptionOrNull() ?: Exception("Pot deposit failed")
                            }
                            nodeOutputJson = "{\"deposited\": true, \"pot_id\": \"$potId\", \"amount_pence\": $amountToDeposit}"
                        } else {
                            nodeOutputJson = "{\"simulated\": true, \"pot_id\": \"$potId\", \"amount_pence\": $amountToDeposit}"
                        }

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.EXTERNAL_HTTP_REQUEST -> {
                        val url = interpolate(currentNode.config.httpUrl, contextData)
                        val body = interpolate(currentNode.config.httpBodyTemplate, contextData)
                        if (url.isNotBlank() && url.startsWith("http")) {
                            val httpResp = genericHttpClient.request(url) {
                                method = HttpMethod.parse(currentNode.config.httpMethod)
                                setBody(body)
                                contentType(ContentType.Application.Json)
                            }
                            nodeOutputJson = "{\"status\": ${httpResp.status.value}, \"response\": \"${httpResp.bodyAsText().take(200)}\"}"
                        } else {
                            nodeOutputJson = "{\"skipped\": true, \"reason\": \"No valid HTTP URL configured\"}"
                        }

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    NodeType.NOTIFY_ANDROID -> {
                        val title = interpolate(currentNode.config.notifyTitle, contextData)
                        val message = interpolate(currentNode.config.notifyMessage, contextData)
                        showAndroidNotification(title, message)
                        nodeOutputJson = "{\"notification_dispatched\": true, \"title\": \"$title\"}"

                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }

                    else -> {
                        queueNextNodes(currentNode.id, connections, nodes, queue)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error executing node ${currentNode.id}")
                stepStatus = ExecutionStatus.FAILED
                stepError = e.message ?: "Execution error"
                overallStatus = ExecutionStatus.FAILED
                errorSummary = "Node '${currentNode.title}' failed: $stepError"
            }

            val stepFinished = System.currentTimeMillis()
            steps.add(
                NodeExecutionStep(
                    nodeId = currentNode.id,
                    nodeTitle = currentNode.title,
                    nodeType = currentNode.type,
                    status = stepStatus,
                    startedAt = stepStart,
                    finishedAt = stepFinished,
                    durationMs = stepFinished - stepStart,
                    inputJson = inputJsonSnapshot,
                    outputJson = nodeOutputJson,
                    errorMessage = stepError
                )
            )

            // Stop branch if failed
            if (stepStatus == ExecutionStatus.FAILED) {
                break
            }
        }

        val finishedAt = System.currentTimeMillis()
        val log = ExecutionLog(
            id = runId,
            workflowId = workflow.id,
            workflowName = workflow.name,
            triggerSource = source,
            status = overallStatus,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = finishedAt - startedAt,
            steps = steps,
            errorSummary = errorSummary,
            triggerPayloadJson = json.encodeToString(transaction)
        )

        database.executionLogDao().insertLog(log)
        database.workflowDao().updateWorkflow(
            workflow.copy(
                totalExecutions = workflow.totalExecutions + 1,
                lastExecutionStatus = overallStatus.name,
                lastExecutedAt = finishedAt,
                updatedAt = System.currentTimeMillis()
            )
        )

        log
    }

    private fun queueNextNodes(
        currentNodeId: String,
        connections: List<FlowConnection>,
        nodes: Map<String, FlowNode>,
        queue: ArrayDeque<Pair<FlowNode, String>>
    ) {
        val nextConns = connections.filter { it.fromNodeId == currentNodeId }
        for (c in nextConns) {
            val nextNode = nodes[c.toNodeId]
            if (nextNode != null && nextNode.enabled) {
                queue.add(Pair(nextNode, c.toPortId))
            }
        }
    }

    private fun evaluateFilter(
        config: NodeConfig,
        transaction: MonzoTransaction,
        context: Map<String, Any>
    ): Boolean {
        // Transaction direction (Debit vs Credit)
        when (config.filterTransactionType.uppercase()) {
            "DEBIT" -> if (!transaction.isSpend) return false
            "CREDIT" -> if (!transaction.isIncome) return false
        }

        // Transaction status (Pending vs Settled vs Declined)
        when (config.filterTransactionStatus.uppercase()) {
            "PENDING" -> if (!transaction.settled.isNullOrBlank() || !transaction.decline_reason.isNullOrBlank()) return false
            "SETTLED", "COMPLETED" -> if (transaction.settled.isNullOrBlank()) return false
            "DECLINED" -> if (transaction.decline_reason.isNullOrBlank()) return false
        }

        // Category check
        if (config.filterCategory.isNotBlank() && !config.filterCategory.equals("all", ignoreCase = true)) {
            if (!transaction.category.equals(config.filterCategory, ignoreCase = true)) {
                return false
            }
        }

        // Merchant check
        if (config.filterMerchant.isNotBlank()) {
            val merchantMatch = transaction.displayTitle.contains(config.filterMerchant, ignoreCase = true) ||
                transaction.description.contains(config.filterMerchant, ignoreCase = true)
            if (!merchantMatch) return false
        }

        // Currency check
        when (config.filterCurrency.uppercase()) {
            "GBP" -> if (!transaction.currency.equals("GBP", ignoreCase = true)) return false
            "FOREIGN" -> if (transaction.currency.equals("GBP", ignoreCase = true)) return false
            "EUR" -> if (!transaction.currency.equals("EUR", ignoreCase = true)) return false
            "USD" -> if (!transaction.currency.equals("USD", ignoreCase = true)) return false
        }

        // Amount checks (in pence)
        val amountAbs = transaction.amountAbs
        when (config.filterComparison) {
            "GREATER_THAN" -> {
                if (amountAbs <= config.filterMinAmountPence) return false
            }
            "LESS_THAN" -> {
                if (amountAbs >= config.filterMaxAmountPence) return false
            }
            "BETWEEN" -> {
                if (amountAbs < config.filterMinAmountPence || amountAbs > config.filterMaxAmountPence) return false
            }
        }

        return true
    }

    private fun calculateRoundUp(amountPence: Long, roundTargetPence: Int): Long {
        val remainder = amountPence % roundTargetPence
        return if (remainder == 0L) 0L else (roundTargetPence - remainder)
    }

    private fun interpolate(template: String, data: Map<String, Any>): String {
        var result = template
        data.forEach { (key, value) ->
            result = result.replace("{{$key}}", value.toString())
            result = result.replace("{{ $key }}", value.toString())
        }
        return result
    }

    private fun showAndroidNotification(title: String, message: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Timber.e(e, "Error showing system notification")
        }
    }

    private fun matchesTrigger(node: FlowNode, transaction: MonzoTransaction, source: String): Boolean {
        if (node.category != NodeCategory.TRIGGER) return false

        // Manual or Simulator overrides
        if (source == "Simulator" && node.type == NodeType.TRIGGER_SIMULATOR) return true
        if (source == "Manual" && node.type == NodeType.TRIGGER_MANUAL) return true

        val config = node.config

        // 1. Transaction Direction Filter (Debit vs Credit)
        when (config.filterTransactionType.uppercase()) {
            "DEBIT" -> if (!transaction.isSpend) return false
            "CREDIT" -> if (!transaction.isIncome) return false
        }

        // 2. Transaction Status / Settlement Filter (Pending vs Settled vs Declined)
        when (config.filterTransactionStatus.uppercase()) {
            "PENDING" -> if (!transaction.settled.isNullOrBlank() || !transaction.decline_reason.isNullOrBlank()) return false
            "SETTLED", "COMPLETED" -> if (transaction.settled.isNullOrBlank()) return false
            "DECLINED" -> if (transaction.decline_reason.isNullOrBlank()) return false
        }

        // 3. Category Filter
        if (config.filterCategory.isNotBlank() && !config.filterCategory.equals("all", ignoreCase = true)) {
            if (!transaction.category.contains(config.filterCategory, ignoreCase = true)) return false
        }

        // 4. Merchant Filter
        if (config.filterMerchant.isNotBlank()) {
            val merchantMatch = transaction.displayTitle.contains(config.filterMerchant, ignoreCase = true) ||
                transaction.description.contains(config.filterMerchant, ignoreCase = true)
            if (!merchantMatch) return false
        }

        // 5. Amount Min / Max Filter (in pence)
        val amountAbs = transaction.amountAbs
        if (config.filterMinAmountPence > 0L && amountAbs < config.filterMinAmountPence) return false
        if (config.filterMaxAmountPence < Long.MAX_VALUE && amountAbs > config.filterMaxAmountPence) return false

        // 6. Currency Filter
        when (config.filterCurrency.uppercase()) {
            "GBP" -> if (!transaction.currency.equals("GBP", ignoreCase = true)) return false
            "FOREIGN" -> if (transaction.currency.equals("GBP", ignoreCase = true)) return false
            "EUR" -> if (!transaction.currency.equals("EUR", ignoreCase = true)) return false
            "USD" -> if (!transaction.currency.equals("USD", ignoreCase = true)) return false
        }

        // Node type-specific semantic rules
        return when (node.type) {
            NodeType.TRIGGER_WEBHOOK -> true // Raw Webhook with user-configured filters applied!
            NodeType.TRIGGER_MONEY_RECEIVED -> transaction.isIncome || transaction.category.equals("income", ignoreCase = true)
            NodeType.TRIGGER_CARD_SPEND -> transaction.isSpend && transaction.decline_reason.isNullOrBlank()
            NodeType.TRIGGER_TRANSFER_OUT -> transaction.isSpend && (
                transaction.category.contains("transfer", ignoreCase = true) ||
                transaction.description.contains("transfer", ignoreCase = true) ||
                transaction.description.contains("sent to", ignoreCase = true)
            )
            NodeType.TRIGGER_DIRECT_DEBIT -> transaction.category.equals("bills", ignoreCase = true) ||
                transaction.description.contains("direct debit", ignoreCase = true) ||
                transaction.description.contains("standing order", ignoreCase = true)
            NodeType.TRIGGER_POT_MOVE -> transaction.category.equals("pot", ignoreCase = true) ||
                transaction.description.contains("pot", ignoreCase = true) ||
                transaction.description.contains("round up", ignoreCase = true)
            NodeType.TRIGGER_DECLINE -> !transaction.decline_reason.isNullOrBlank() ||
                transaction.description.contains("declined", ignoreCase = true)
            NodeType.TRIGGER_FOREIGN_CURRENCY -> !transaction.currency.equals("GBP", ignoreCase = true) ||
                transaction.metadata.containsKey("local_currency")
            NodeType.TRIGGER_LOW_BALANCE -> transaction.metadata.containsKey("low_balance_triggered") || transaction.amountAbs < 5000L
            NodeType.TRIGGER_POLL -> source == "Poller"
            NodeType.TRIGGER_SIMULATOR -> source == "Simulator"
            NodeType.TRIGGER_MANUAL -> source == "Manual"
            else -> false
        }
    }

    companion object {
        const val CHANNEL_ID = "monzoflow_alerts"
    }
}
