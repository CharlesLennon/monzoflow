package com.monzoflow.app.data.local

import androidx.room.*
import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.Workflow
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {

    // Workflows
    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun getAllWorkflows(): Flow<List<Workflow>>

    @Query("SELECT * FROM workflows WHERE active = 1")
    suspend fun getActiveWorkflows(): List<Workflow>

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun getWorkflowById(id: String): Workflow?

    @Query("SELECT * FROM workflows WHERE id = :id")
    fun getWorkflowFlow(id: String): Flow<Workflow?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: Workflow)

    @Update
    suspend fun updateWorkflow(workflow: Workflow)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: String)

    // Nodes
    @Query("SELECT * FROM flow_nodes WHERE workflowId = :workflowId")
    fun getNodesForWorkflow(workflowId: String): Flow<List<FlowNode>>

    @Query("SELECT * FROM flow_nodes WHERE workflowId = :workflowId")
    suspend fun getNodesSnapshot(workflowId: String): List<FlowNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: FlowNode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<FlowNode>)

    @Update
    suspend fun updateNode(node: FlowNode)

    @Query("UPDATE flow_nodes SET posX = :posX, posY = :posY WHERE id = :nodeId")
    suspend fun updateNodePosition(nodeId: String, posX: Float, posY: Float)

    @Query("DELETE FROM flow_nodes WHERE id = :nodeId")
    suspend fun deleteNode(nodeId: String)

    @Query("DELETE FROM flow_nodes WHERE workflowId = :workflowId")
    suspend fun deleteNodesForWorkflow(workflowId: String)

    // Connections
    @Query("SELECT * FROM flow_connections WHERE workflowId = :workflowId")
    fun getConnectionsForWorkflow(workflowId: String): Flow<List<FlowConnection>>

    @Query("SELECT * FROM flow_connections WHERE workflowId = :workflowId")
    suspend fun getConnectionsSnapshot(workflowId: String): List<FlowConnection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: FlowConnection)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<FlowConnection>)

    @Query("DELETE FROM flow_connections WHERE id = :connectionId")
    suspend fun deleteConnection(connectionId: String)

    @Query("DELETE FROM flow_connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteConnectionsForNode(nodeId: String)

    @Query("DELETE FROM flow_connections WHERE workflowId = :workflowId")
    suspend fun deleteConnectionsForWorkflow(workflowId: String)
}
