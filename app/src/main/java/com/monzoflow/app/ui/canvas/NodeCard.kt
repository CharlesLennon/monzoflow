package com.monzoflow.app.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.NodeCategory
import com.monzoflow.app.data.model.NodeType
import com.monzoflow.app.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NodeCard(
    node: FlowNode,
    isSelected: Boolean,
    isBeingDragged: Boolean = false,
    isConnectingSource: Boolean = false,
    isConnectTarget: Boolean = false,
    onTap: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (dragAmountX: Float, dragAmountY: Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onStartWiring: (portId: String) -> Unit = {},
    onPortDragStart: (portId: String, isInput: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val categoryColor = when (node.category) {
        NodeCategory.TRIGGER -> TriggerColor
        NodeCategory.MONZO_ACTION -> MonzoCoral
        NodeCategory.LOGIC -> LogicColor
        NodeCategory.TRANSFORM -> TransformColor
        NodeCategory.EXTERNAL -> ExternalColor
        NodeCategory.NOTIFICATION -> OutputColor
    }

    val icon: ImageVector = when (node.type) {
        NodeType.TRIGGER_WEBHOOK -> Icons.Default.CloudSync
        NodeType.TRIGGER_MONEY_RECEIVED -> Icons.Default.MonetizationOn
        NodeType.TRIGGER_CARD_SPEND -> Icons.Default.CreditCard
        NodeType.TRIGGER_TRANSFER_OUT -> Icons.Default.Send
        NodeType.TRIGGER_DIRECT_DEBIT -> Icons.Default.ReceiptLong
        NodeType.TRIGGER_POT_MOVE -> Icons.Default.Savings
        NodeType.TRIGGER_DECLINE -> Icons.Default.Block
        NodeType.TRIGGER_FOREIGN_CURRENCY -> Icons.Default.CurrencyExchange
        NodeType.TRIGGER_LOW_BALANCE -> Icons.Default.Warning
        NodeType.TRIGGER_SIMULATOR -> Icons.Default.Science
        NodeType.TRIGGER_POLL -> Icons.Default.Schedule
        NodeType.TRIGGER_MANUAL -> Icons.Default.PlayArrow
        NodeType.MONZO_PUSH_FEED -> Icons.Default.DynamicFeed
        NodeType.MONZO_ANNOTATE -> Icons.Default.Label
        NodeType.MONZO_POT_DEPOSIT -> Icons.Default.Savings
        NodeType.MONZO_POT_WITHDRAW -> Icons.Default.AccountBalanceWallet
        NodeType.MONZO_GET_BALANCE -> Icons.Default.AccountBalance
        NodeType.LOGIC_FILTER -> Icons.Default.FilterAlt
        NodeType.LOGIC_SPLIT -> Icons.Default.CallSplit
        NodeType.LOGIC_DELAY -> Icons.Default.HourglassEmpty
        NodeType.TRANSFORM_ROUNDUP -> Icons.Default.PriceChange
        NodeType.TRANSFORM_PERCENTAGE -> Icons.Default.Percent
        NodeType.TRANSFORM_TEMPLATE -> Icons.Default.TextFields
        NodeType.EXTERNAL_HTTP_REQUEST -> Icons.Default.Http
        NodeType.NOTIFY_ANDROID -> Icons.Default.Notifications
    }

    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(220.dp)
            .shadow(
                elevation = when {
                    isBeingDragged -> 16.dp
                    isConnectingSource || isConnectTarget -> 10.dp
                    isSelected -> 8.dp
                    else -> 4.dp
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(
                width = if (isBeingDragged || isSelected || isConnectingSource || isConnectTarget) 2.dp else 1.dp,
                color = when {
                    isBeingDragged -> MintGreen
                    isConnectingSource -> MonzoCoral
                    isConnectTarget -> MintGreen
                    isSelected -> categoryColor
                    else -> SurfaceBorder
                },
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = {
                        totalDragDistance = 0f
                        onDragStart()
                    },
                    onDragEnd = {
                        if (totalDragDistance < 10f) {
                            onTap()
                        }
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragDistance += (abs(dragAmount.x) + abs(dragAmount.y))
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar (Tactile Grip Handle, Category Pill & Settings Icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isBeingDragged) categoryColor.copy(alpha = 0.32f) else categoryColor.copy(alpha = 0.18f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isBeingDragged) MintGreen.copy(alpha = 0.5f) else categoryColor.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tactile Drag Grip Indicator
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag Grip Handle",
                    tint = when {
                        isBeingDragged -> MintGreen
                        isConnectingSource -> MonzoCoral
                        isConnectTarget -> MintGreen
                        else -> TextSecondary.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CanvasBackground,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Text(
                    text = node.category.name.replace("_", " "),
                    color = if (isBeingDragged) MintGreen else categoryColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Configure / Settings Gear Icon
                IconButton(
                    onClick = onTap,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure Node",
                        tint = if (isSelected) categoryColor else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Status Ring
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isBeingDragged) MintGreen else StatusSuccess)
                )
            }

            // Node Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = node.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )

                if (node.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = node.subtitle,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Config Details Badge
                val configSummary = getConfigSummary(node)
                if (configSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = configSummary,
                            color = TextAccent,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Port Sockets Bar (Bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input Port (Left)
                if (node.inputs.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnectTarget) MintGreen else TextAccent)
                                .border(1.5.dp, CanvasBackground, CircleShape)
                        )
                        Text(
                            text = if (isConnectTarget) "CONNECT ➔" else "IN",
                            color = if (isConnectTarget) MintGreen else TextMuted,
                            fontSize = 8.5.sp,
                            fontWeight = if (isConnectTarget) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "TRIGGER",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Output Port & Wire Link Button (Right)
                if (node.outputs.isNotEmpty()) {
                    val outPort = node.outputs.first()
                    Surface(
                        color = if (isConnectingSource) MonzoCoral else categoryColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isConnectingSource) MonzoCoral else categoryColor),
                        modifier = Modifier.pointerInput(outPort.id) {
                            detectTapGestures {
                                onStartWiring(outPort.id)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Wire Node",
                                tint = if (isConnectingSource) Color.White else categoryColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isConnectingSource) "WIRING..." else "WIRE OUT",
                                color = if (isConnectingSource) Color.White else categoryColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnectingSource) Color.White else categoryColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getConfigSummary(node: FlowNode): String {
    val typeFilter = when (node.config.filterTransactionType.uppercase()) {
        "DEBIT" -> "Debit"
        "CREDIT" -> "Credit"
        else -> ""
    }
    val statusFilter = when (node.config.filterTransactionStatus.uppercase()) {
        "PENDING" -> "Pending"
        "SETTLED", "COMPLETED" -> "Settled"
        "DECLINED" -> "Declined"
        else -> ""
    }
    val filterSummary = listOf(typeFilter, statusFilter).filter { it.isNotBlank() }.joinToString(" • ")

    return when (node.type) {
        NodeType.TRIGGER_WEBHOOK,
        NodeType.TRIGGER_MONEY_RECEIVED,
        NodeType.TRIGGER_CARD_SPEND,
        NodeType.TRIGGER_TRANSFER_OUT,
        NodeType.TRIGGER_DIRECT_DEBIT,
        NodeType.TRIGGER_POT_MOVE,
        NodeType.TRIGGER_DECLINE,
        NodeType.TRIGGER_FOREIGN_CURRENCY,
        NodeType.TRIGGER_LOW_BALANCE -> {
            if (filterSummary.isNotBlank()) filterSummary else ""
        }
        NodeType.LOGIC_FILTER -> {
            val cat = node.config.filterCategory.takeIf { it.isNotBlank() } ?: "all"
            val amt = if (node.config.filterMinAmountPence > 0) "> £${node.config.filterMinAmountPence / 100}" else ""
            val base = "Cat: $cat $amt".trim()
            if (filterSummary.isNotBlank()) "$base ($filterSummary)" else base
        }
        NodeType.TRANSFORM_ROUNDUP -> "Round to £${node.config.roundUpTargetPence / 100}.00"
        NodeType.TRANSFORM_PERCENTAGE -> "${node.config.potPercentage.toInt()}% Allocation"
        NodeType.MONZO_PUSH_FEED -> node.config.feedTitleTemplate.take(24)
        NodeType.MONZO_ANNOTATE -> node.config.annotateTags.take(24)
        NodeType.MONZO_POT_DEPOSIT -> "Pot: ${node.config.potName.ifBlank { "Savings" }}"
        NodeType.EXTERNAL_HTTP_REQUEST -> node.config.httpMethod + " " + node.config.httpUrl.take(18)
        NodeType.NOTIFY_ANDROID -> "Alert: " + node.config.notifyTitle.take(18)
        else -> ""
    }
}
