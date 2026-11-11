package com.monzoflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.monzoflow.app.data.model.ExecutionLog
import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.Workflow

@Database(
    entities = [
        Workflow::class,
        FlowNode::class,
        FlowConnection::class,
        ExecutionLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workflowDao(): WorkflowDao
    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monzoflow.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
