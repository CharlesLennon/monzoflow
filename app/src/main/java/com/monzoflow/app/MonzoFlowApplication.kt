package com.monzoflow.app

import android.app.Application
import com.monzoflow.app.data.local.AppDatabase
import com.monzoflow.app.data.secure.SecureCredentialManager
import com.monzoflow.app.engine.BuiltinTemplates
import com.monzoflow.app.engine.WorkflowExecutor
import com.monzoflow.app.monzo.MonzoApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MonzoFlowApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val credentialManager: SecureCredentialManager by lazy { SecureCredentialManager(this) }
    val monzoClient: MonzoApiClient by lazy { MonzoApiClient(credentialManager) }
    val workflowExecutor: WorkflowExecutor by lazy {
        WorkflowExecutor(this, database, credentialManager, monzoClient)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize default templates if first run
        CoroutineScope(Dispatchers.IO).launch {
            val existing = database.workflowDao().getActiveWorkflows()
            if (existing.isEmpty()) {
                val defaults = BuiltinTemplates.createDefaultWorkflows()
                for ((wf, nodes, conns) in defaults) {
                    database.workflowDao().insertWorkflow(wf)
                    database.workflowDao().insertNodes(nodes)
                    database.workflowDao().insertConnections(conns)
                }
                Timber.i("MonzoFlow default workflow templates seeded successfully.")
            }
        }
    }
}
