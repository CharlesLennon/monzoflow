package com.monzoflow.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.NodeCategory
import com.monzoflow.app.data.model.NodeType
import com.monzoflow.app.theme.*

data class NodeTemplateItem(
    val type: NodeType,
    val category: NodeCategory,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNodeSheet(
    onDismiss: () -> Unit,
    onNodeSelected: (type: NodeType, category: NodeCategory, title: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<NodeCategory?>(null) }

    val allTemplates = remember {
        listOf(
            // Triggers
            NodeTemplateItem(NodeType.TRIGGER_WEBHOOK, NodeCategory.TRIGGER, "Monzo Webhook", "Fires instantly on transaction.created events", Icons.Default.CloudSync),
            NodeTemplateItem(NodeType.TRIGGER_SIMULATOR, NodeCategory.TRIGGER, "Webhook Simulator", "Test lab with presets (Coffee, Salary, Grocery)", Icons.Default.Science),
            NodeTemplateItem(NodeType.TRIGGER_MANUAL, NodeCategory.TRIGGER, "Manual Tap Trigger", "Executes pipeline on manual tap", Icons.Default.PlayArrow),
            NodeTemplateItem(NodeType.TRIGGER_POLL, NodeCategory.TRIGGER, "Periodic Poller", "Checks balance & transactions periodically", Icons.Default.Schedule),

            // Monzo Actions
            NodeTemplateItem(NodeType.MONZO_PUSH_FEED, NodeCategory.MONZO_ACTION, "Push Monzo Feed Item", "Pushes interactive card into official Monzo app feed", Icons.Default.DynamicFeed),
            NodeTemplateItem(NodeType.MONZO_ANNOTATE, NodeCategory.MONZO_ACTION, "Annotate Transaction", "Appends notes and custom #tags to transaction", Icons.Default.Label),
            NodeTemplateItem(NodeType.MONZO_POT_DEPOSIT, NodeCategory.MONZO_ACTION, "Deposit to Savings Pot", "Moves money or spare change into designated pot", Icons.Default.Savings),
            NodeTemplateItem(NodeType.MONZO_POT_WITHDRAW, NodeCategory.MONZO_ACTION, "Withdraw from Pot", "Draws down funds from savings pot back to account", Icons.Default.AccountBalanceWallet),
            NodeTemplateItem(NodeType.MONZO_GET_BALANCE, NodeCategory.MONZO_ACTION, "Query Monzo Balance", "Fetches live balance and today's total spend", Icons.Default.AccountBalance),

            // Logic
            NodeTemplateItem(NodeType.LOGIC_FILTER, NodeCategory.LOGIC, "Condition Filter", "If category, amount, or merchant matches criteria", Icons.Default.FilterAlt),
            NodeTemplateItem(NodeType.LOGIC_DELAY, NodeCategory.LOGIC, "Delay / Wait", "Pauses workflow execution for specified time", Icons.Default.HourglassEmpty),

            // Transform
            NodeTemplateItem(NodeType.TRANSFORM_ROUNDUP, NodeCategory.TRANSFORM, "Spare Change Round-Up", "Rounds purchase up to nearest £1, £5, or £10", Icons.Default.PriceChange),
            NodeTemplateItem(NodeType.TRANSFORM_PERCENTAGE, NodeCategory.TRANSFORM, "Percentage Calculator", "Calculates % allocation (e.g. 20% savings)", Icons.Default.Percent),
            NodeTemplateItem(NodeType.TRANSFORM_TEMPLATE, NodeCategory.TRANSFORM, "Text Template", "Constructs formatted text with {{amount}} & {{merchant}}", Icons.Default.TextFields),

            // External & Notifications
            NodeTemplateItem(NodeType.EXTERNAL_HTTP_REQUEST, NodeCategory.EXTERNAL, "HTTP Webhook Request", "POST / GET to Telegram, Discord, or Home Assistant", Icons.Default.Http),
            NodeTemplateItem(NodeType.NOTIFY_ANDROID, NodeCategory.NOTIFICATION, "Phone Notification", "Sends Android system alert with sound and vibration", Icons.Default.Notifications)
        )
    }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == null) allTemplates else allTemplates.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SurfaceBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ADD AUTOMATION NODE",
                color = MonzoCoral,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = "Select a trigger, Monzo action, or logic step for your canvas",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryChip(name = "All", isSelected = selectedCategory == null) { selectedCategory = null }
                CategoryChip(name = "Triggers", isSelected = selectedCategory == NodeCategory.TRIGGER) { selectedCategory = NodeCategory.TRIGGER }
                CategoryChip(name = "Monzo", isSelected = selectedCategory == NodeCategory.MONZO_ACTION) { selectedCategory = NodeCategory.MONZO_ACTION }
                CategoryChip(name = "Logic", isSelected = selectedCategory == NodeCategory.LOGIC) { selectedCategory = NodeCategory.LOGIC }
                CategoryChip(name = "Transform", isSelected = selectedCategory == NodeCategory.TRANSFORM) { selectedCategory = NodeCategory.TRANSFORM }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTemplates) { item ->
                    val catColor = when (item.category) {
                        NodeCategory.TRIGGER -> TriggerColor
                        NodeCategory.MONZO_ACTION -> MonzoCoral
                        NodeCategory.LOGIC -> LogicColor
                        NodeCategory.TRANSFORM -> TransformColor
                        NodeCategory.EXTERNAL -> ExternalColor
                        NodeCategory.NOTIFICATION -> OutputColor
                    }

                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNodeSelected(item.type, item.category, item.title)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(catColor.copy(alpha = 0.20f))
                                    .border(1.dp, catColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = item.icon, contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = item.description, color = TextSecondary, fontSize = 10.5.sp)
                            }

                            Text(
                                text = item.category.name.replace("_", " "),
                                color = catColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MonzoCoral else SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MonzoCoral else SurfaceBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
