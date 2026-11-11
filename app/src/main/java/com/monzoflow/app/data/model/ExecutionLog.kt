package com.monzoflow.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class ExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

@Serializable
data class NodeExecutionStep(
    val nodeId: String,
    val nodeTitle: String,
    val nodeType: NodeType,
    val status: ExecutionStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
    val inputJson: String,
    val outputJson: String,
    val errorMessage: String? = null
)

@Entity(tableName = "execution_logs")
@Serializable
data class ExecutionLog(
    @PrimaryKey
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val triggerSource: String, // Webhook, Simulator, Manual, Poller
    val status: ExecutionStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
    val steps: List<NodeExecutionStep> = emptyList(),
    val errorSummary: String? = null,
    val triggerPayloadJson: String = ""
)
