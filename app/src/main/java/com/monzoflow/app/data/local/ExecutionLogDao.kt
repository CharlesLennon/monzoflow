package com.monzoflow.app.data.local

import androidx.room.*
import com.monzoflow.app.data.model.ExecutionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {

    @Query("SELECT * FROM execution_logs ORDER BY startedAt DESC")
    fun getAllLogs(): Flow<List<ExecutionLog>>

    @Query("SELECT * FROM execution_logs WHERE workflowId = :workflowId ORDER BY startedAt DESC")
    fun getLogsForWorkflow(workflowId: String): Flow<List<ExecutionLog>>

    @Query("SELECT * FROM execution_logs WHERE id = :id")
    suspend fun getLogById(id: String): ExecutionLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog)

    @Query("DELETE FROM execution_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("DELETE FROM execution_logs")
    suspend fun clearAllLogs()
}
