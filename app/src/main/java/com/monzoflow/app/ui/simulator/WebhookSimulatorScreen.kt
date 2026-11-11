package com.monzoflow.app.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.ExecutionLog
import com.monzoflow.app.data.model.ExecutionStatus
import com.monzoflow.app.data.model.MonzoMerchant
import com.monzoflow.app.data.model.MonzoTransaction
import com.monzoflow.app.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

data class SimulatorPreset(
    val name: String,
    val icon: String,
    val merchantName: String,
    val amountPounds: Double,
    val isSpend: Boolean,
    val category: String,
    val notes: String = ""
)

@Composable
fun WebhookSimulatorScreen(
    onFireSimulatedTransaction: suspend (MonzoTransaction) -> List<ExecutionLog>,
    onInspectRun: (ExecutionLog) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val presets = remember {
        listOf(
            SimulatorPreset("Pret Coffee", "☕", "Pret A Manger", 3.60, true, "eating_out", "Morning flat white"),
            SimulatorPreset("Starbucks", "☕", "Starbucks Coffee", 4.25, true, "eating_out", "Caramel Macchiato"),
            SimulatorPreset("Tesco Groceries", "🛒", "Tesco Express", 28.50, true, "groceries", "Weekly essentials"),
            SimulatorPreset("Monthly Salary", "💰", "ACME Corporation", 2850.00, false, "income", "November Salary"),
            SimulatorPreset("Spotify", "🎵", "Spotify UK", 10.99, true, "entertainment", "Monthly subscription"),
            SimulatorPreset("TFL Underground", "🚇", "Transport for London", 3.40, true, "transport", "Zone 1-2 commute")
        )
    }

    var merchantName by remember { mutableStateOf("Pret A Manger") }
    var amountPoundsText by remember { mutableStateOf("3.60") }
    var isSpend by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("eating_out") }
    var notes by remember { mutableStateOf("Morning flat white") }

    var isRunning by remember { mutableStateOf(false) }
    var lastExecutedLogs by remember { mutableStateOf<List<ExecutionLog>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Screen Title Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Science, contentDescription = null, tint = LogicColor, modifier = Modifier.size(24.dp))
            Column {
                Text(
                    text = "MONZO WEBHOOK TEST LAB",
                    color = MonzoCoral,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Simulate live transaction events to test workflows safely",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Carousel
        Text(
            text = "QUICK REAL-WORLD PRESETS",
            color = TextAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.clickable {
                        merchantName = preset.merchantName
                        amountPoundsText = "%.2f".format(preset.amountPounds)
                        isSpend = preset.isSpend
                        category = preset.category
                        notes = preset.notes
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = preset.icon, fontSize = 14.sp)
                        Column {
                            Text(text = preset.name, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${if (preset.isSpend) "-£" else "+£"}${"%.2f".format(preset.amountPounds)}",
                                color = if (preset.isSpend) MonzoCoral else MintGreen,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Transaction Parameter Editor
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "EVENT PAYLOAD PARAMETERS",
                    color = TextAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("Merchant / Payee Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountPoundsText,
                        onValueChange = { amountPoundsText = it },
                        label = { Text("Amount (£)") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                    )

                    // Spend vs Income Toggle
                    Surface(
                        color = if (isSpend) MonzoCoral.copy(alpha = 0.20f) else MintGreen.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSpend) MonzoCoral else MintGreen),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .clickable { isSpend = !isSpend }
                    ) {
                        Text(
                            text = if (isSpend) "SPEND (-)" else "INCOME (+)",
                            color = if (isSpend) MonzoCoral else MintGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (eating_out, groceries, transport, general)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Description") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MonzoCoral, unfocusedBorderColor = SurfaceBorder)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Fire Trigger Button
                Button(
                    onClick = {
                        val pounds = amountPoundsText.toDoubleOrNull() ?: 0.0
                        val pence = (pounds * 100).toLong()
                        val finalAmountPence = if (isSpend) -pence else pence

                        val mockTx = MonzoTransaction(
                            id = "sim_tx_${UUID.randomUUID().toString().take(8)}",
                            amount = finalAmountPence,
                            currency = "GBP",
                            description = merchantName,
                            notes = notes,
                            category = category,
                            created = "2026-11-11T07:30:00Z",
                            merchant = MonzoMerchant(
                                id = "merch_sim",
                                name = merchantName,
                                category = category
                            ),
                            account_id = "acc_simulated"
                        )

                        isRunning = true
                        coroutineScope.launch {
                            val results = onFireSimulatedTransaction(mockTx)
                            lastExecutedLogs = results
                            isRunning = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonzoCoral),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "EXECUTING WORKFLOW...", fontFamily = FontFamily.Monospace)
                    } else {
                        Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "FIRE TEST TRANSACTION INTO WORKFLOWS", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Live Execution Results Card
        if (lastExecutedLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "EXECUTION RESULTS (${lastExecutedLogs.size} WORKFLOWS TRIGGERED)",
                color = TextAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lastExecutedLogs.forEach { log ->
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (log.status == ExecutionStatus.SUCCESS) StatusSuccess else StatusFailed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectRun(log) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = log.workflowName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Status: ${log.status.name} • Steps: ${log.steps.size} • Time: ${log.durationMs}ms",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = { onInspectRun(log) },
                                colors = ButtonDefaults.buttonColors(containerColor = MonzoCoral),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(text = "INSPECT", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
