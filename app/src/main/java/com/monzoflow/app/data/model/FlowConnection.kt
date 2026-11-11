package com.monzoflow.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "flow_connections")
@Serializable
data class FlowConnection(
    @PrimaryKey
    val id: String,
    val workflowId: String,
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String,
    val animated: Boolean = true
)
