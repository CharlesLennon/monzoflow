package com.monzoflow.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "workflows")
@Serializable
data class Workflow(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val totalExecutions: Long = 0L,
    val lastExecutionStatus: String? = null,
    val lastExecutedAt: Long? = null
)
