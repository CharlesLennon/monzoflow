package com.monzoflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.monzoflow.app.MonzoFlowApplication
import com.monzoflow.app.data.model.MonzoWebhookPayload
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import timber.log.Timber

class WebhookServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: EmbeddedServer<*, *>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Starting Webhook Engine..."))

        serviceScope.launch {
            try {
                val app = application as MonzoFlowApplication
                val port = app.credentialManager.webhookServerPort

                server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    install(ContentNegotiation) {
                        json(json)
                    }

                    routing {
                        get("/health") {
                            call.respondText(
                                "{\"status\": \"UP\", \"service\": \"MonzoFlow Webhook Engine\", \"version\": \"1.0.0\"}",
                                ContentType.Application.Json
                            )
                        }

                        post("/webhook/monzo") {
                            try {
                                val bodyText = call.receiveText()
                                Timber.d("Received Monzo Webhook: $bodyText")
                                val payload = json.decodeFromString<MonzoWebhookPayload>(bodyText)

                                // Trigger Workflow Execution Pipeline asynchronously
                                serviceScope.launch {
                                    app.workflowExecutor.handleIncomingTransaction(payload.data, source = "Webhook")
                                }

                                call.respond(HttpStatusCode.OK, mapOf("status" to "processed"))
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing incoming Monzo webhook")
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid payload")))
                            }
                        }
                    }
                }.start(wait = false)

                updateForegroundNotification("Listening on port $port for Monzo Webhooks")
                Timber.i("MonzoFlow embedded webhook server started on port $port")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start embedded webhook server")
                updateForegroundNotification("Error: ${e.message}")
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            server?.stop(1000, 2000)
            server = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Timber.i("MonzoFlow embedded webhook server stopped")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MonzoFlow Webhook Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service maintaining on-device Monzo webhook listener"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MonzoFlow Ingress Engine")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        server?.stop(500, 1000)
    }

    companion object {
        const val ACTION_START = "com.monzoflow.START_SERVER"
        const val ACTION_STOP = "com.monzoflow.STOP_SERVER"
        private const val CHANNEL_ID = "monzoflow_webhook_channel"
        private const val NOTIFICATION_ID = 2048

        fun start(context: Context) {
            val intent = Intent(context, WebhookServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebhookServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
