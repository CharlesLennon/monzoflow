package com.monzoflow.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.NodeConfig
import com.monzoflow.app.data.model.NodeType
import com.monzoflow.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeConfigSheet(
    node: FlowNode,
    onDismiss: () -> Unit,
    onSaveConfig: (updatedNode: FlowNode) -> Unit,
    onDeleteNode: (nodeId: String) -> Unit
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

            // Specific Node Editors
            when (node.type) {
                NodeType.LOGIC_FILTER -> {
                    Text(text = "Filter Criteria", color = TextAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = config.filterCategory,
                        onValueChange = { config = config.copy(filterCategory = it) },
                        label = { Text("Category (e.g. eating_out, groceries, general)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.filterMerchant,
                        onValueChange = { config = config.copy(filterMerchant = it) },
                        label = { Text("Merchant Name Contains (e.g. Starbucks, Tesco)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = (config.filterMinAmountPence / 100f).toString(),
                        onValueChange = {
                            val pence = (it.toDoubleOrNull() ?: 0.0) * 100
                            config = config.copy(filterMinAmountPence = pence.toLong())
                        },
                        label = { Text("Minimum Amount (£)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                }

                else -> {
                    Text(text = "Standard Node Settings", color = TextSecondary, fontSize = 11.sp)
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
