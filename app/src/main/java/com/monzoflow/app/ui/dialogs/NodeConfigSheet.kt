package com.monzoflow.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.NodeCategory
import com.monzoflow.app.data.model.NodeConfig
import com.monzoflow.app.data.model.NodeType
import com.monzoflow.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeConfigSheet(
    node: FlowNode,
    allNodes: List<FlowNode> = emptyList(),
    connections: List<FlowConnection> = emptyList(),
    onDismiss: () -> Unit,
    onSaveConfig: (updatedNode: FlowNode) -> Unit,
    onDeleteNode: (nodeId: String) -> Unit,
    onAddConnection: (toNodeId: String) -> Unit = {},
    onDeleteConnection: (connectionId: String) -> Unit = {}
) {
    var title by remember { mutableStateOf(node.title) }
    var subtitle by remember { mutableStateOf(node.subtitle) }
    var config by remember { mutableStateOf(node.config) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SurfaceBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONFIGURE NODE",
                        color = MonzoCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = node.type.name,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = {
                        onDeleteNode(node.id)
                        onDismiss()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Node", tint = Color(0xFFEF4444))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Node Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MonzoCoral,
                    unfocusedBorderColor = SurfaceBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Field
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Node Subtitle / Note") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MonzoCoral,
                    unfocusedBorderColor = SurfaceBorder
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =====================================================================
            // 1. Transaction Type & Settlement Status Filters (Triggers & Logic Filter)
            // =====================================================================
            val isTriggerOrFilter = node.category == NodeCategory.TRIGGER || node.type == NodeType.LOGIC_FILTER
            if (isTriggerOrFilter) {
                Text(
                    text = "TRANSACTION & WEBHOOK FILTERS",
                    color = MonzoCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Direction / Type: ALL vs DEBIT vs CREDIT
                Text(text = "Transaction Type / Direction", color = TextAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "All Types",
                        "DEBIT" to "Debit (Spend)",
                        "CREDIT" to "Credit (Income)"
                    ).forEach { (key, label) ->
                        val selected = config.filterTransactionType.equals(key, ignoreCase = true)
                        Surface(
                            color = if (selected) MonzoCoral.copy(alpha = 0.25f) else SurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MonzoCoral else SurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { config = config.copy(filterTransactionType = key) }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MonzoCoral else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status: ALL vs PENDING vs SETTLED vs DECLINED
                Text(text = "Transaction Status / Settlement", color = TextAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "ALL" to "All",
                        "PENDING" to "Pending",
                        "SETTLED" to "Settled",
                        "DECLINED" to "Declined"
                    ).forEach { (key, label) ->
                        val selected = config.filterTransactionStatus.equals(key, ignoreCase = true)
                        Surface(
                            color = if (selected) MintGreen.copy(alpha = 0.25f) else SurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MintGreen else SurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { config = config.copy(filterTransactionStatus = key) }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MintGreen else TextSecondary,
                                fontSize = 8.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter with Quick Chips
                Text(text = "Monzo Category Filter", color = TextAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = config.filterCategory,
                    onValueChange = { config = config.copy(filterCategory = it) },
                    placeholder = { Text("Leave blank for all categories") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("eating_out", "groceries", "bills", "shopping", "transfers", "income", "entertainment", "general").forEach { cat ->
                        val isSelected = config.filterCategory.equals(cat, ignoreCase = true)
                        Surface(
                            color = if (isSelected) MonzoCoral.copy(alpha = 0.2f) else SurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) MonzoCoral else SurfaceBorder),
                            modifier = Modifier.clickable {
                                config = config.copy(filterCategory = if (isSelected) "" else cat)
                            }
                        ) {
                            Text(
                                text = cat.replace("_", " "),
                                color = if (isSelected) MonzoCoral else TextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Merchant Filter
                Text(text = "Merchant Name Filter", color = TextAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = config.filterMerchant,
                    onValueChange = { config = config.copy(filterMerchant = it) },
                    placeholder = { Text("e.g. Tesco, Costa, Amazon, Shell") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Amount Min / Max (£)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (config.filterMinAmountPence > 0) (config.filterMinAmountPence / 100.0).toString() else "",
                        onValueChange = {
                            val pence = ((it.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            config = config.copy(filterMinAmountPence = pence)
                        },
                        label = { Text("Min Amount (£)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = if (config.filterMaxAmountPence < Long.MAX_VALUE) (config.filterMaxAmountPence / 100.0).toString() else "",
                        onValueChange = {
                            val pounds = it.toDoubleOrNull()
                            val pence = if (pounds != null) (pounds * 100).toLong() else Long.MAX_VALUE
                            config = config.copy(filterMaxAmountPence = pence)
                        },
                        label = { Text("Max Amount (£)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Currency Filter
                Text(text = "Currency Filter", color = TextAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "Any", "GBP" to "GBP (£)", "EUR" to "EUR (€)", "FOREIGN" to "Non-GBP").forEach { (curr, label) ->
                        val selected = config.filterCurrency.equals(curr, ignoreCase = true)
                        Surface(
                            color = if (selected) TextAccent.copy(alpha = 0.25f) else SurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) TextAccent else SurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { config = config.copy(filterCurrency = curr) }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) TextAccent else TextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // =====================================================================
            // 2. Action Specific Configurations
            // =====================================================================
            when (node.type) {
                NodeType.MONZO_PUSH_FEED -> {
                    Text(text = "Monzo In-App Card Template", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = config.feedTitleTemplate,
                        onValueChange = { config = config.copy(feedTitleTemplate = it) },
                        label = { Text("Feed Item Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.feedBodyTemplate,
                        onValueChange = { config = config.copy(feedBodyTemplate = it) },
                        label = { Text("Body Template (supports {{amount}}, {{merchant}})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.feedBackgroundColor,
                        onValueChange = { config = config.copy(feedBackgroundColor = it) },
                        label = { Text("Card Accent Hex Color (e.g. #FF4D4D)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                NodeType.MONZO_ANNOTATE -> {
                    Text(text = "Transaction Annotation & Tags", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = config.annotateNotesTemplate,
                        onValueChange = { config = config.copy(annotateNotesTemplate = it) },
                        label = { Text("Transaction Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.annotateTags,
                        onValueChange = { config = config.copy(annotateTags = it) },
                        label = { Text("Custom Tags (e.g. #groceries #tax-deductible)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                NodeType.MONZO_POT_DEPOSIT -> {
                    Text(text = "Savings Pot Configuration", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = config.potName,
                        onValueChange = { config = config.copy(potName = it) },
                        label = { Text("Target Pot Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.potAmountType,
                        onValueChange = { config = config.copy(potAmountType = it) },
                        label = { Text("Deposit Type (ROUNDUP_SPARE, EXACT, PERCENTAGE)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                NodeType.TRANSFORM_ROUNDUP -> {
                    Text(text = "Round-Up Calibration", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = (config.roundUpTargetPence / 100).toString(),
                        onValueChange = {
                            val pounds = it.toIntOrNull() ?: 1
                            config = config.copy(roundUpTargetPence = pounds * 100)
                        },
                        label = { Text("Round to Nearest (£)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                NodeType.NOTIFY_ANDROID -> {
                    Text(text = "Phone Notification Setup", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = config.notifyTitle,
                        onValueChange = { config = config.copy(notifyTitle = it) },
                        label = { Text("Notification Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.notifyMessage,
                        onValueChange = { config = config.copy(notifyMessage = it) },
                        label = { Text("Notification Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                else -> {}
            }

            // =====================================================================
            // 3. Wires & Connections Manager
            // =====================================================================
            Divider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "WIRES & CONNECTIONS",
                color = MintGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val outgoingConns = connections.filter { it.fromNodeId == node.id }
            if (outgoingConns.isEmpty()) {
                Text(
                    text = "No outgoing wires from this node yet.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "Outgoing Wires (${outgoingConns.size}):",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                outgoingConns.forEach { conn ->
                    val targetNode = allNodes.firstOrNull { it.id == conn.toNodeId }
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MintGreen, modifier = Modifier.size(14.dp))
                                Text(
                                    text = targetNode?.title ?: "Node (${conn.toNodeId.take(8)})",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { onDeleteConnection(conn.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Wire", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add wire to another node button & dropdown
            val connectableNodes = allNodes.filter { it.id != node.id && outgoingConns.none { conn -> conn.toNodeId == it.id } }
            if (connectableNodes.isNotEmpty()) {
                var showConnectDropdown by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { showConnectDropdown = true },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "+ WIRE TO ANOTHER NODE...", color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    DropdownMenu(
                        expanded = showConnectDropdown,
                        onDismissRequest = { showConnectDropdown = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        connectableNodes.forEach { targetNode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(text = targetNode.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = targetNode.category.name.replace("_", " "), color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                },
                                onClick = {
                                    onAddConnection(targetNode.id)
                                    showConnectDropdown = false
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    val updated = node.copy(title = title, subtitle = subtitle, config = config)
                    onSaveConfig(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MonzoCoral),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "SAVE NODE CONFIGURATION", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
