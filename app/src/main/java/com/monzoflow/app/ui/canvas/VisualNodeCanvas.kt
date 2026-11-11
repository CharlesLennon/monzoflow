package com.monzoflow.app.ui.canvas

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.Workflow
import com.monzoflow.app.theme.*
import kotlin.math.roundToInt

@Composable
fun VisualNodeCanvas(
    workflow: Workflow,
    nodes: List<FlowNode>,
    connections: List<FlowConnection>,
    onNodeMoved: (nodeId: String, newX: Float, newY: Float) -> Unit,
    onNodeSelected: (FlowNode) -> Unit,
    onAddNodeClick: () -> Unit,
    onConnectNodes: (fromNodeId: String, fromPortId: String, toNodeId: String, toPortId: String) -> Unit,
    onDeleteConnection: (connectionId: String) -> Unit,
    onTestRunClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var draggingNodeId by remember { mutableStateOf<String?>(null) }
    var connectingFromNodeId by remember { mutableStateOf<String?>(null) }
    var connectingFromPortId by remember { mutableStateOf<String?>(null) }

    // Live interactive wire drag state
    var draggingWireSourceNodeId by remember { mutableStateOf<String?>(null) }
    var draggingWireSourcePortId by remember { mutableStateOf<String?>(null) }
    var liveWireStartScreen by remember { mutableStateOf<Offset?>(null) }
    var liveWireEndScreen by remember { mutableStateOf<Offset?>(null) }
    var hoveredTargetNodeId by remember { mutableStateOf<String?>(null) }

    // In-memory node positions for instant 60/120fps dragging with zero database disk I/O lag
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }

    // Sync in-memory positions from database nodes when not actively dragging
    LaunchedEffect(nodes) {
        val currentIds = nodes.map { it.id }.toSet()
        nodePositions.keys.retainAll(currentIds)
        for (node in nodes) {
            if (!nodePositions.containsKey(node.id) || draggingNodeId != node.id) {
                nodePositions[node.id] = Offset(node.posX, node.posY)
            }
        }
    }

    // Pulse animation along active wires
    val infiniteTransition = rememberInfiniteTransition(label = "wire_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .pointerInput(draggingNodeId) {
                if (draggingNodeId == null) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 2.2f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
            }
    ) {
        // 1. Dot-Matrix Grid Background & Connection Wires Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40f * scale
            val startX = panX % gridSpacing
            val startY = panY % gridSpacing

            // Draw Background Dots
            var x = startX
            while (x < size.width) {
                var y = startY
                while (y < size.height) {
                    drawCircle(
                        color = CanvasGridDot.copy(alpha = 0.6f),
                        radius = 1.5f * scale,
                        center = Offset(x, y)
                    )
                    y += gridSpacing
                }
                x += gridSpacing
            }

            // Draw Cubic Bézier Wires using real-time live in-memory positions
            for (conn in connections) {
                val fromNode = nodeMap[conn.fromNodeId]
                val toNode = nodeMap[conn.toNodeId]
                if (fromNode != null && toNode != null) {
                    val fromPos = nodePositions[fromNode.id] ?: Offset(fromNode.posX, fromNode.posY)
                    val toPos = nodePositions[toNode.id] ?: Offset(toNode.posX, toNode.posY)

                    // Node card width ~220dp, height ~95dp
                    val cardWidthPx = 220f * density * scale
                    val cardHeightPx = 95f * density * scale

                    val startScreenX = (fromPos.x * scale) + panX + cardWidthPx - (10f * scale)
                    val startScreenY = (fromPos.y * scale) + panY + (cardHeightPx * 0.85f)

                    val endScreenX = (toPos.x * scale) + panX + (10f * scale)
                    val endScreenY = (toPos.y * scale) + panY + (cardHeightPx * 0.85f)

                    ConnectionWire.drawWire(
                        drawScope = this,
                        start = Offset(startScreenX, startScreenY),
                        end = Offset(endScreenX, endScreenY),
                        color = if (conn.fromPortId == "out_true") MintGreen else Color(0xFF38BDF8),
                        pulseProgress = if (conn.animated) pulseProgress else 0f,
                        strokeWidth = 3f * scale
                    )
                }
            }

            // 1b. Live Interactive Wire Drag Rendering
            val startPt = liveWireStartScreen
            val rawEndPt = liveWireEndScreen
            if (startPt != null && rawEndPt != null) {
                // If a target node is hovered, snap to its input port
                val endPt = if (hoveredTargetNodeId != null) {
                    val targetNode = nodeMap[hoveredTargetNodeId]
                    if (targetNode != null) {
                        val targetPos = nodePositions[targetNode.id] ?: Offset(targetNode.posX, targetNode.posY)
                        val snapped = WireConnectionEngine.calculateInputPortPos(
                            nodeX = targetPos.x,
                            nodeY = targetPos.y,
                            cardWidth = 220f,
                            cardHeight = 95f,
                            panX = panX,
                            panY = panY,
                            scale = scale
                        )
                        Offset(snapped.x, snapped.y)
                    } else {
                        rawEndPt
                    }
                } else {
                    rawEndPt
                }

                val wireColor = if (hoveredTargetNodeId != null) MintGreen else MonzoCoral

                ConnectionWire.drawWire(
                    drawScope = this,
                    start = startPt,
                    end = endPt,
                    color = wireColor,
                    pulseProgress = pulseProgress,
                    strokeWidth = 3.5f * scale
                )

                // Glowing socket cursor indicator at dragged position
                drawCircle(
                    color = wireColor.copy(alpha = 0.4f),
                    radius = 14f * scale,
                    center = endPt
                )
                drawCircle(
                    color = wireColor,
                    radius = 8f * scale,
                    center = endPt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f * scale,
                    center = endPt
                )
            }
        }

        // 2. Interactive Node Cards Layer
        Box(modifier = Modifier.fillMaxSize()) {
            for (node in nodes) {
                val currentPos = nodePositions[node.id] ?: Offset(node.posX, node.posY)
                val screenX = ((currentPos.x * scale) + panX).roundToInt()
                val screenY = ((currentPos.y * scale) + panY).roundToInt()

                val isSource = connectingFromNodeId == node.id || draggingWireSourceNodeId == node.id
                val isTarget = (connectingFromNodeId != null && connectingFromNodeId != node.id) ||
                    hoveredTargetNodeId == node.id ||
                    (draggingWireSourceNodeId != null && draggingWireSourceNodeId != node.id && node.inputs.isNotEmpty())

                NodeCard(
                    node = node,
                    isSelected = selectedNodeId == node.id,
                    isBeingDragged = draggingNodeId == node.id,
                    isConnectingSource = isSource,
                    isConnectTarget = isTarget,
                    onTap = {
                        if (connectingFromNodeId != null && connectingFromNodeId != node.id) {
                            // Finish connection
                            onConnectNodes(connectingFromNodeId!!, connectingFromPortId ?: "out_default", node.id, "in_default")
                            connectingFromNodeId = null
                            connectingFromPortId = null
                        } else if (connectingFromNodeId == node.id) {
                            // Cancel wiring if tapping source node again
                            connectingFromNodeId = null
                            connectingFromPortId = null
                        } else {
                            selectedNodeId = node.id
                            onNodeSelected(node)
                        }
                    },
                    onStartWiring = { portId ->
                        connectingFromNodeId = node.id
                        connectingFromPortId = portId
                    },
                    onWireDragStart = { portId, _ ->
                        val pos = nodePositions[node.id] ?: Offset(node.posX, node.posY)
                        val portPos = WireConnectionEngine.calculateOutputPortPos(
                            nodeX = pos.x,
                            nodeY = pos.y,
                            cardWidth = 220f,
                            cardHeight = 95f,
                            panX = panX,
                            panY = panY,
                            scale = scale
                        )
                        draggingWireSourceNodeId = node.id
                        draggingWireSourcePortId = portId
                        liveWireStartScreen = Offset(portPos.x, portPos.y)
                        liveWireEndScreen = Offset(portPos.x, portPos.y)
                        hoveredTargetNodeId = null
                    },
                    onWireDrag = { dragDelta ->
                        liveWireEndScreen?.let { current ->
                            val updated = Offset(current.x + dragDelta.x, current.y + dragDelta.y)
                            liveWireEndScreen = updated

                            val canvasPt = WireConnectionEngine.screenToCanvas(
                                screenPoint = WirePoint(updated.x, updated.y),
                                panX = panX,
                                panY = panY,
                                scale = scale
                            )
                            val hit = WireConnectionEngine.hitTestTargetNode(
                                canvasPoint = canvasPt,
                                nodes = nodes,
                                nodePositions = nodePositions.mapValues { WirePoint(it.value.x, it.value.y) },
                                sourceNodeId = node.id,
                                cardWidth = 220f,
                                cardHeight = 95f,
                                hitMargin = 32f
                            )
                            hoveredTargetNodeId = hit?.id
                        }
                    },
                    onWireDragEnd = {
                        val srcId = draggingWireSourceNodeId
                        val srcPort = draggingWireSourcePortId ?: "out_default"
                        val tgtId = hoveredTargetNodeId

                        if (srcId != null && tgtId != null) {
                            val validation = WireConnectionEngine.validateConnection(
                                fromNodeId = srcId,
                                toNodeId = tgtId,
                                existingConnections = connections,
                                nodes = nodes
                            )
                            if (validation is ConnectionValidationResult.Valid) {
                                val toPortId = validation.toNode.inputs.firstOrNull()?.id ?: "in_default"
                                onConnectNodes(srcId, srcPort, tgtId, toPortId)
                            }
                        }

                        draggingWireSourceNodeId = null
                        draggingWireSourcePortId = null
                        liveWireStartScreen = null
                        liveWireEndScreen = null
                        hoveredTargetNodeId = null
                    },
                    onWireDragCancel = {
                        draggingWireSourceNodeId = null
                        draggingWireSourcePortId = null
                        liveWireStartScreen = null
                        liveWireEndScreen = null
                        hoveredTargetNodeId = null
                    },
                    onDragStart = {
                        draggingNodeId = node.id
                        selectedNodeId = node.id
                    },
                    onDrag = { dragX, dragY ->
                        val current = nodePositions[node.id] ?: Offset(node.posX, node.posY)
                        val updated = Offset(
                            x = current.x + (dragX / scale),
                            y = current.y + (dragY / scale)
                        )
                        nodePositions[node.id] = updated
                    },
                    onDragEnd = {
                        val finalPos = nodePositions[node.id]
                        draggingNodeId = null
                        if (finalPos != null) {
                            onNodeMoved(node.id, finalPos.x, finalPos.y)
                        }
                    },
                    modifier = Modifier.offset { IntOffset(screenX, screenY) }
                )
            }
        }

        // 3. Top Canvas Floating Info & Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Workflow Name & Status Pill
            Surface(
                color = SurfaceDark.copy(alpha = 0.90f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (workflow.active) MintGreen else StatusIdle)
                    )
                    Text(
                        text = workflow.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = "${nodes.size} Nodes • ${connections.size} Wires",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Test Simulation Launcher
            Surface(
                color = MonzoCoral,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { onTestRunClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(text = "TEST RUN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // 4. Connecting Helper Banner (Active Wire Mode) - Positioned at Top for Visibility
        val activeWireSourceId = connectingFromNodeId ?: draggingWireSourceNodeId
        if (activeWireSourceId != null) {
            val sourceNode = nodeMap[activeWireSourceId]
            Surface(
                color = SurfaceElevated.copy(alpha = 0.96f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MintGreen),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MintGreen)
                    )
                    Column {
                        Text(
                            text = if (draggingWireSourceNodeId != null) "DRAGGING WIRE" else "WIRING MODE ACTIVE",
                            color = MintGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "From: ${sourceNode?.title ?: "Node"} ➔ Drag or tap destination node to link",
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            connectingFromNodeId = null
                            connectingFromPortId = null
                            draggingWireSourceNodeId = null
                            draggingWireSourcePortId = null
                            liveWireStartScreen = null
                            liveWireEndScreen = null
                            hoveredTargetNodeId = null
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Wiring", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 5. Canvas Zoom Controls & Add Node FAB (Bottom End)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add Node FAB
            FloatingActionButton(
                onClick = onAddNodeClick,
                containerColor = MonzoCoral,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Node", modifier = Modifier.size(28.dp))
            }

            // Reset Zoom
            FloatingActionButton(
                onClick = {
                    panX = 0f
                    panY = 0f
                    scale = 1.0f
                },
                containerColor = SurfaceElevated,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = "Reset Zoom", modifier = Modifier.size(18.dp))
            }
        }
    }
}
