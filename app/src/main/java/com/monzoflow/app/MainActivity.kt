package com.monzoflow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.*
import com.monzoflow.app.engine.BuiltinTemplates
import com.monzoflow.app.service.WebhookServerService
import com.monzoflow.app.theme.*
import com.monzoflow.app.ui.canvas.VisualNodeCanvas
import com.monzoflow.app.ui.dialogs.AddNodeSheet
import com.monzoflow.app.ui.dialogs.NodeConfigSheet
import com.monzoflow.app.ui.dialogs.RunInspectorDialog
import com.monzoflow.app.ui.history.ExecutionHistoryScreen
import com.monzoflow.app.ui.settings.MonzoSettingsScreen
import com.monzoflow.app.ui.simulator.WebhookSimulatorScreen
import com.monzoflow.app.ui.workflows.WorkflowsScreen
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

enum class AppTab(val label: String, val icon: ImageVector) {
    CANVAS("Canvas", Icons.Default.Hub),
    WORKFLOWS("Workflows", Icons.Default.AccountTree),
    SIMULATOR("Simulator", Icons.Default.Science),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Monzo", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MonzoFlowApplication
        val db = app.database
        val credentialManager = app.credentialManager
        val monzoClient = app.monzoClient
        val workflowExecutor = app.workflowExecutor

        // Automatically start embedded webhook server if not already running
        try {
            WebhookServerService.start(this)
        } catch (e: Exception) {
            Timber.w(e, "Could not start webhook background service automatically")
        }

        // Handle OAuth callback intent if launched via deep link
        handleOAuthCallbackIntent(intent, credentialManager)

        setContent {
            MonzoFlowTheme {
                val coroutineScope = rememberCoroutineScope()
                var currentTab by remember { mutableStateOf(AppTab.CANVAS) }

                val workflows by db.workflowDao().getAllWorkflows().collectAsState(initial = emptyList())
                var selectedWorkflowId by remember { mutableStateOf<String?>(null) }

                // Automatically pick first workflow if none selected
                LaunchedEffect(workflows) {
                    if (selectedWorkflowId == null && workflows.isNotEmpty()) {
                        selectedWorkflowId = workflows.first().id
                    }
                }

                val currentWorkflow = workflows.firstOrNull { it.id == selectedWorkflowId }
                    ?: workflows.firstOrNull()
                    ?: Workflow("default", "Coffee Saver", active = true)

                val nodes by db.workflowDao().getNodesForWorkflow(currentWorkflow.id).collectAsState(initial = emptyList())
                val connections by db.workflowDao().getConnectionsForWorkflow(currentWorkflow.id).collectAsState(initial = emptyList())
                val executionLogs by db.executionLogDao().getAllLogs().collectAsState(initial = emptyList())

                // Dialog states
                var showAddNodeSheet by remember { mutableStateOf(false) }
                var inspectingNode by remember { mutableStateOf<FlowNode?>(null) }
                var viewingExecutionLog by remember { mutableStateOf<ExecutionLog?>(null) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            tonalElevation = 8.dp
                        ) {
                            AppTab.values().forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            tint = if (selected) MonzoCoral else TextSecondary
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            color = if (selected) MonzoCoral else TextSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MonzoCoral.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppTab.CANVAS -> {
                                VisualNodeCanvas(
                                    workflow = currentWorkflow,
                                    nodes = nodes,
                                    connections = connections,
                                    onNodeMoved = { nodeId, newX, newY ->
                                        coroutineScope.launch {
                                            db.workflowDao().updateNodePosition(nodeId, newX, newY)
                                        }
                                    },
                                    onNodeSelected = { node ->
                                        inspectingNode = node
                                    },
                                    onAddNodeClick = { showAddNodeSheet = true },
                                    onConnectNodes = { fromNodeId, fromPortId, toNodeId, toPortId ->
                                        coroutineScope.launch {
                                            val conn = FlowConnection(
                                                id = "conn_${UUID.randomUUID().toString().take(8)}",
                                                workflowId = currentWorkflow.id,
                                                fromNodeId = fromNodeId,
                                                fromPortId = fromPortId,
                                                toNodeId = toNodeId,
                                                toPortId = toPortId
                                            )
                                            db.workflowDao().insertConnection(conn)
                                        }
                                    },
                                    onDeleteConnection = { connId ->
                                        coroutineScope.launch {
                                            db.workflowDao().deleteConnection(connId)
                                        }
                                    },
                                    onTestRunClick = {
                                        currentTab = AppTab.SIMULATOR
                                    }
                                )
                            }

                            AppTab.WORKFLOWS -> {
                                WorkflowsScreen(
                                    workflows = workflows,
                                    onSelectWorkflow = { wf ->
                                        selectedWorkflowId = wf.id
                                        currentTab = AppTab.CANVAS
                                    },
                                    onToggleWorkflow = { wf, active ->
                                        coroutineScope.launch {
                                            db.workflowDao().updateWorkflow(wf.copy(active = active))
                                        }
                                    },
                                    onCreateNewWorkflow = {
                                        coroutineScope.launch {
                                            val newWf = Workflow(
                                                id = "wf_${UUID.randomUUID().toString().take(8)}",
                                                name = "New Automation Pipeline",
                                                description = "Custom visual workflow",
                                                active = true
                                            )
                                            val startTrigger = FlowNode(
                                                id = "node_trig_${UUID.randomUUID().toString().take(6)}",
                                                workflowId = newWf.id,
                                                type = NodeType.TRIGGER_WEBHOOK,
                                                category = NodeCategory.TRIGGER,
                                                title = "Monzo Webhook",
                                                subtitle = "Live transactions",
                                                posX = 80f,
                                                posY = 140f
                                            )
                                            db.workflowDao().insertWorkflow(newWf)
                                            db.workflowDao().insertNode(startTrigger)
                                            selectedWorkflowId = newWf.id
                                            currentTab = AppTab.CANVAS
                                        }
                                    },
                                    onResetTemplates = {
                                        coroutineScope.launch {
                                            val defaults = BuiltinTemplates.createDefaultWorkflows()
                                            for ((wf, nList, cList) in defaults) {
                                                db.workflowDao().insertWorkflow(wf)
                                                db.workflowDao().insertNodes(nList)
                                                db.workflowDao().insertConnections(cList)
                                            }
                                            Toast.makeText(this@MainActivity, "Templates restored", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            AppTab.SIMULATOR -> {
                                WebhookSimulatorScreen(
                                    onFireSimulatedTransaction = { mockTx ->
                                        workflowExecutor.handleIncomingTransaction(mockTx, source = "Simulator")
                                    },
                                    onInspectRun = { log ->
                                        viewingExecutionLog = log
                                    }
                                )
                            }

                            AppTab.HISTORY -> {
                                ExecutionHistoryScreen(
                                    logs = executionLogs,
                                    onSelectLog = { log ->
                                        viewingExecutionLog = log
                                    },
                                    onClearLogs = {
                                        coroutineScope.launch {
                                            db.executionLogDao().clearAllLogs()
                                        }
                                    }
                                )
                            }

                            AppTab.SETTINGS -> {
                                MonzoSettingsScreen(
                                    credentialManager = credentialManager,
                                    monzoClient = monzoClient
                                )
                            }
                        }

                        // Add Node Bottom Sheet
                        if (showAddNodeSheet) {
                            AddNodeSheet(
                                onDismiss = { showAddNodeSheet = false },
                                onNodeSelected = { type, category, title ->
                                    coroutineScope.launch {
                                        val newNode = FlowNode(
                                            id = "node_${UUID.randomUUID().toString().take(8)}",
                                            workflowId = currentWorkflow.id,
                                            type = type,
                                            category = category,
                                            title = title,
                                            subtitle = type.name.lowercase().replace("_", " "),
                                            posX = 400f + (nodes.size * 20f),
                                            posY = 140f + (nodes.size * 20f)
                                        )
                                        db.workflowDao().insertNode(newNode)
                                    }
                                }
                            )
                        }

                        // Node Configuration Editor
                        inspectingNode?.let { node ->
                            NodeConfigSheet(
                                node = node,
                                onDismiss = { inspectingNode = null },
                                onSaveConfig = { updatedNode ->
                                    coroutineScope.launch {
                                        db.workflowDao().updateNode(updatedNode)
                                        inspectingNode = null
                                    }
                                },
                                onDeleteNode = { nodeId ->
                                    coroutineScope.launch {
                                        db.workflowDao().deleteNode(nodeId)
                                        db.workflowDao().deleteConnectionsForNode(nodeId)
                                        inspectingNode = null
                                    }
                                }
                            )
                        }

                        // Execution Inspector Dialog
                        viewingExecutionLog?.let { log ->
                            RunInspectorDialog(
                                log = log,
                                onDismiss = { viewingExecutionLog = null }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val app = application as MonzoFlowApplication
        handleOAuthCallbackIntent(intent, app.credentialManager)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?, credentialManager: com.monzoflow.app.data.secure.SecureCredentialManager) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "monzoflow" && data.host == "oauth") {
            val code = data.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                Toast.makeText(this, "OAuth2 authorization code captured! Exchanging tokens...", Toast.LENGTH_LONG).show()
                Timber.i("Monzo OAuth authorization code received: $code")
            }
        }
    }
}
