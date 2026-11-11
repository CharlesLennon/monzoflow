package com.monzoflow.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.MonzoAccount
import com.monzoflow.app.data.model.MonzoBalance
import com.monzoflow.app.data.model.MonzoPot
import com.monzoflow.app.data.secure.AuthMode
import com.monzoflow.app.data.secure.SecureCredentialManager
import com.monzoflow.app.monzo.MonzoApiClient
import com.monzoflow.app.service.WebhookServerService
import com.monzoflow.app.theme.*
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun MonzoSettingsScreen(
    credentialManager: SecureCredentialManager,
    monzoClient: MonzoApiClient,
    updateManager: com.monzoflow.app.update.AppUpdateManager? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val updateState = updateManager?.updateState?.collectAsState()?.value

    var authMode by remember { mutableStateOf(credentialManager.authMode) }
    var patText by remember { mutableStateOf(credentialManager.personalAccessToken) }
    var clientIdText by remember { mutableStateOf(credentialManager.oauthClientId) }
    var clientSecretText by remember { mutableStateOf(credentialManager.oauthClientSecret) }
    var webhookPortText by remember { mutableStateOf(credentialManager.webhookServerPort.toString()) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionSuccess by remember { mutableStateOf<Boolean?>(null) }
    var accountsList by remember { mutableStateOf<List<MonzoAccount>>(emptyList()) }
    var balanceData by remember { mutableStateOf<MonzoBalance?>(null) }
    var potsList by remember { mutableStateOf<List<MonzoPot>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isWebhookServiceRunning by remember { mutableStateOf(true) }

    val localIp = remember { getLocalIpAddress() }
    val webhookUrl = "http://$localIp:${credentialManager.webhookServerPort}/webhook/monzo"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MonzoCoral, modifier = Modifier.size(24.dp))
            Column {
                Text(
                    text = "MONZO DEVELOPER SETTINGS",
                    color = MonzoCoral,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Configure Monzo credentials, webhooks, and active accounts",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Auth Mode Selector (PAT vs OAuth2)
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "AUTHENTICATION MODE",
                    color = TextAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            authMode = AuthMode.PERSONAL_ACCESS_TOKEN
                            credentialManager.authMode = AuthMode.PERSONAL_ACCESS_TOKEN
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (authMode == AuthMode.PERSONAL_ACCESS_TOKEN) MonzoCoral else SurfaceElevated
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "PERSONAL TOKEN", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            authMode = AuthMode.OAUTH2
                            credentialManager.authMode = AuthMode.OAUTH2
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (authMode == AuthMode.OAUTH2) MonzoCoral else SurfaceElevated
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "OAUTH2 CLIENT", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (authMode == AuthMode.PERSONAL_ACCESS_TOKEN) {
                    OutlinedTextField(
                        value = patText,
                        onValueChange = {
                            patText = it
                            credentialManager.personalAccessToken = it
                        },
                        label = { Text("Personal Access Token (PAT)") },
                        placeholder = { Text("ey...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "👉 Generate token at developers.monzo.com/api/playground",
                        color = MonzoCoral,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.monzo.com/api/playground"))
                            context.startActivity(intent)
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = clientIdText,
                        onValueChange = {
                            clientIdText = it
                            credentialManager.oauthClientId = it
                        },
                        label = { Text("OAuth2 Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clientSecretText,
                        onValueChange = {
                            clientSecretText = it
                            credentialManager.oauthClientSecret = it
                        },
                        label = { Text("OAuth2 Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Redirect URI: monzoflow://oauth/callback",
                        color = TextAccent,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test Connection Button
                Button(
                    onClick = {
                        isTestingConnection = true
                        errorMessage = null
                        coroutineScope.launch {
                            val accRes = monzoClient.getAccounts()
                            if (accRes.isSuccess) {
                                accountsList = accRes.getOrDefault(emptyList())
                                if (accountsList.isNotEmpty()) {
                                    val accId = accountsList.first().id
                                    credentialManager.selectedAccountId = accId
                                    val balRes = monzoClient.getBalance(accId)
                                    if (balRes.isSuccess) {
                                        balanceData = balRes.getOrNull()
                                    }
                                }
                                val potsRes = monzoClient.getPots()
                                if (potsRes.isSuccess) {
                                    potsList = potsRes.getOrDefault(emptyList())
                                }
                                connectionSuccess = true
                            } else {
                                connectionSuccess = false
                                errorMessage = accRes.exceptionOrNull()?.message
                            }
                            isTestingConnection = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isTestingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(color = CanvasBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "CONNECTING...", color = CanvasBackground, fontFamily = FontFamily.Monospace)
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CanvasBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "TEST MONZO CONNECTION", color = CanvasBackground, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                if (connectionSuccess == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "✅ Connected to Monzo! ${accountsList.size} accounts found.", color = MintGreen, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                } else if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "❌ Connection failed: $errorMessage", color = StatusFailed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Monzo Live Account & Pots Card (if connected)
        if (balanceData != null || potsList.isNotEmpty()) {
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE MONZO BALANCE",
                            color = MintGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        balanceData?.let {
                            Text(
                                text = it.balanceFormatted,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    balanceData?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spent Today: ${it.spendTodayFormatted} • Total with Pots: ${it.totalBalanceFormatted}",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (potsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "SAVINGS POTS (${potsList.size})", color = TextAccent, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        potsList.forEach { pot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "🏺 ${pot.name}", color = TextPrimary, fontSize = 10.sp)
                                Text(text = pot.balanceFormatted, color = TextAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. On-Device Webhook Ingress Server Card
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ON-DEVICE WEBHOOK ENGINE",
                            color = TextAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Embedded Ktor HTTP server for real-time webhooks",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }

                    Switch(
                        checked = isWebhookServiceRunning,
                        onCheckedChange = { running ->
                            isWebhookServiceRunning = running
                            if (running) {
                                WebhookServerService.start(context)
                            } else {
                                WebhookServerService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MonzoCoral, checkedTrackColor = MonzoCoral.copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CanvasBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "WEBHOOK ENDPOINT URL", color = TextMuted, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                        Text(text = webhookUrl, color = TextPrimary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    }

                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(webhookUrl)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextAccent, modifier = Modifier.size(13.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tip: For production internet webhooks, pipe this port through Cloudflare Tunnel, Tailscale Funnel, or your router port forwarding.",
                    color = TextSecondary,
                    fontSize = 8.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. In-App Auto Updater (Like ReconDrive)
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "APP UPDATES & RELEASES",
                            color = MonzoCoral,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "MonzoFlow Version: v${updateManager?.currentVersionName ?: "1.0.1"}",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { updateManager?.checkForUpdates(isManual = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MonzoCoral),
                        shape = RoundedCornerShape(6.dp),
                        enabled = updateState !is com.monzoflow.app.update.UpdateState.Checking
                    ) {
                        if (updateState is com.monzoflow.app.update.UpdateState.Checking) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp))
                        } else {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "CHECK FOR UPDATES", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return "127.0.0.1"
}
