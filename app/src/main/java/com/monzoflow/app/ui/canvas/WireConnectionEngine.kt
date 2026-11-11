package com.monzoflow.app.ui.canvas

import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import java.util.UUID

/**
 * Lightweight 2D point data class for wire positioning and hit-testing calculations.
 */
data class WirePoint(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: WirePoint): WirePoint = WirePoint(x + other.x, y + other.y)
    operator fun minus(other: WirePoint): WirePoint = WirePoint(x - other.x, y - other.y)
}

/**
 * Result of validating a potential connection between two nodes.
 */
sealed class ConnectionValidationResult {
    data class Valid(val fromNode: FlowNode, val toNode: FlowNode) : ConnectionValidationResult()
    object SelfConnection : ConnectionValidationResult()
    data class AlreadyConnected(val connectionId: String) : ConnectionValidationResult()
    object SourceNotFound : ConnectionValidationResult()
    object TargetNotFound : ConnectionValidationResult()
    object TargetHasNoInputs : ConnectionValidationResult()
    object SourceHasNoOutputs : ConnectionValidationResult()
}

/**
 * Result of finishing an interactive wire drag gesture.
 */
sealed class DragConnectionResult {
    data class Success(val connection: FlowConnection, val targetNode: FlowNode) : DragConnectionResult()
    data class Rejected(val reason: ConnectionValidationResult) : DragConnectionResult()
    object Missed : DragConnectionResult()
    object Cancelled : DragConnectionResult()
}

/**
 * Pure-logic engine for coordinate conversion, port positioning, hit testing, and wire validation.
 */
object WireConnectionEngine {

    const val DEFAULT_CARD_WIDTH = 220f
    const val DEFAULT_CARD_HEIGHT = 95f
    const val DEFAULT_HIT_MARGIN = 24f

    fun canvasToScreenX(canvasX: Float, panX: Float, scale: Float): Float = (canvasX * scale) + panX
    fun canvasToScreenY(canvasY: Float, panY: Float, scale: Float): Float = (canvasY * scale) + panY

    fun screenToCanvasX(screenX: Float, panX: Float, scale: Float): Float = (screenX - panX) / scale
    fun screenToCanvasY(screenY: Float, panY: Float, scale: Float): Float = (screenY - panY) / scale

    fun screenToCanvas(screenPoint: WirePoint, panX: Float, panY: Float, scale: Float): WirePoint =
        WirePoint(
            x = screenToCanvasX(screenPoint.x, panX, scale),
            y = screenToCanvasY(screenPoint.y, panY, scale)
        )

    fun canvasToScreen(canvasPoint: WirePoint, panX: Float, panY: Float, scale: Float): WirePoint =
        WirePoint(
            x = canvasToScreenX(canvasPoint.x, panX, scale),
            y = canvasToScreenY(canvasPoint.y, panY, scale)
        )

    /**
     * Calculates the screen coordinate of a node's output port.
     */
    fun calculateOutputPortPos(
        nodeX: Float,
        nodeY: Float,
        cardWidth: Float = DEFAULT_CARD_WIDTH,
        cardHeight: Float = DEFAULT_CARD_HEIGHT,
        panX: Float,
        panY: Float,
        scale: Float
    ): WirePoint {
        val screenLeft = (nodeX * scale) + panX
        val screenTop = (nodeY * scale) + panY
        val cardWidthScaled = cardWidth * scale
        val cardHeightScaled = cardHeight * scale

        return WirePoint(
            x = screenLeft + cardWidthScaled - (10f * scale),
            y = screenTop + (cardHeightScaled * 0.85f)
        )
    }

    /**
     * Calculates the screen coordinate of a node's input port.
     */
    fun calculateInputPortPos(
        nodeX: Float,
        nodeY: Float,
        cardWidth: Float = DEFAULT_CARD_WIDTH,
        cardHeight: Float = DEFAULT_CARD_HEIGHT,
        panX: Float,
        panY: Float,
        scale: Float
    ): WirePoint {
        val screenLeft = (nodeX * scale) + panX
        val screenTop = (nodeY * scale) + panY
        val cardHeightScaled = cardHeight * scale

        return WirePoint(
            x = screenLeft + (10f * scale),
            y = screenTop + (cardHeightScaled * 0.85f)
        )
    }

    /**
     * Calculates the screen coordinate where the dragging wire should connect to.
     * If a target node is hovered, snaps directly to its input port.
     * Otherwise, follows the finger/pointer screen coordinate.
     */
    fun calculateLiveWireEndPoint(
        currentScreenPoint: WirePoint,
        hoveredTargetNodeId: String?,
        nodePositions: Map<String, WirePoint>,
        nodes: List<FlowNode>,
        cardWidth: Float = DEFAULT_CARD_WIDTH,
        cardHeight: Float = DEFAULT_CARD_HEIGHT,
        panX: Float,
        panY: Float,
        scale: Float
    ): WirePoint {
        if (hoveredTargetNodeId == null) return currentScreenPoint
        val targetNode = nodes.firstOrNull { it.id == hoveredTargetNodeId } ?: return currentScreenPoint
        val pos = nodePositions[hoveredTargetNodeId] ?: WirePoint(targetNode.posX, targetNode.posY)
        return calculateInputPortPos(
            nodeX = pos.x,
            nodeY = pos.y,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            panX = panX,
            panY = panY,
            scale = scale
        )
    }

    /**
     * Hit tests a canvas point against all nodes to find a target node that can accept an input wire.
     * Nodes must not be the source node and must have at least one input port.
     */
    fun hitTestTargetNode(
        canvasPoint: WirePoint,
        nodes: List<FlowNode>,
        nodePositions: Map<String, WirePoint>,
        sourceNodeId: String,
        cardWidth: Float = DEFAULT_CARD_WIDTH,
        cardHeight: Float = DEFAULT_CARD_HEIGHT,
        hitMargin: Float = DEFAULT_HIT_MARGIN
    ): FlowNode? {
        for (node in nodes) {
            // Cannot connect to self
            if (node.id == sourceNodeId) continue

            // Must be able to accept input connections
            if (node.inputs.isEmpty()) continue

            val pos = nodePositions[node.id] ?: WirePoint(node.posX, node.posY)
            val left = pos.x - hitMargin
            val right = pos.x + cardWidth + hitMargin
            val top = pos.y - hitMargin
            val bottom = pos.y + cardHeight + hitMargin

            if (canvasPoint.x in left..right && canvasPoint.y in top..bottom) {
                return node
            }
        }
        return null
    }

    /**
     * Validates if a proposed wire between two nodes is valid and allowable.
     */
    fun validateConnection(
        fromNodeId: String,
        toNodeId: String,
        existingConnections: List<FlowConnection>,
        nodes: List<FlowNode>
    ): ConnectionValidationResult {
        if (fromNodeId == toNodeId) {
            return ConnectionValidationResult.SelfConnection
        }

        val existing = existingConnections.firstOrNull { it.fromNodeId == fromNodeId && it.toNodeId == toNodeId }
        if (existing != null) {
            return ConnectionValidationResult.AlreadyConnected(existing.id)
        }

        val nodeMap = nodes.associateBy { it.id }
        val fromNode = nodeMap[fromNodeId] ?: return ConnectionValidationResult.SourceNotFound
        val toNode = nodeMap[toNodeId] ?: return ConnectionValidationResult.TargetNotFound

        if (fromNode.outputs.isEmpty()) {
            return ConnectionValidationResult.SourceHasNoOutputs
        }

        if (toNode.inputs.isEmpty()) {
            return ConnectionValidationResult.TargetHasNoInputs
        }

        return ConnectionValidationResult.Valid(fromNode, toNode)
    }
}

/**
 * State and controller for an active wire drag session.
 */
class WireDragSession {
    var isDragging: Boolean = false
        private set

    var sourceNodeId: String? = null
        private set

    var sourcePortId: String? = null
        private set

    var startCanvasPoint: WirePoint = WirePoint(0f, 0f)
        private set

    var startScreenPoint: WirePoint = WirePoint(0f, 0f)
        private set

    var currentScreenPoint: WirePoint = WirePoint(0f, 0f)
        private set

    var currentCanvasPoint: WirePoint = WirePoint(0f, 0f)
        private set

    var hoveredTargetNodeId: String? = null
        private set

    fun startDrag(
        fromNodeId: String,
        fromPortId: String,
        startCanvasPoint: WirePoint,
        startScreenPoint: WirePoint
    ) {
        this.isDragging = true
        this.sourceNodeId = fromNodeId
        this.sourcePortId = fromPortId
        this.startCanvasPoint = startCanvasPoint
        this.startScreenPoint = startScreenPoint
        this.currentScreenPoint = startScreenPoint
        this.currentCanvasPoint = startCanvasPoint
        this.hoveredTargetNodeId = null
    }

    fun updateDrag(
        currentScreenPoint: WirePoint,
        panX: Float,
        panY: Float,
        scale: Float,
        nodes: List<FlowNode>,
        nodePositions: Map<String, WirePoint>,
        cardWidth: Float = WireConnectionEngine.DEFAULT_CARD_WIDTH,
        cardHeight: Float = WireConnectionEngine.DEFAULT_CARD_HEIGHT,
        hitMargin: Float = WireConnectionEngine.DEFAULT_HIT_MARGIN
    ) {
        if (!isDragging) return

        this.currentScreenPoint = currentScreenPoint
        this.currentCanvasPoint = WireConnectionEngine.screenToCanvas(currentScreenPoint, panX, panY, scale)

        val srcId = sourceNodeId ?: return
        val target = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = currentCanvasPoint,
            nodes = nodes,
            nodePositions = nodePositions,
            sourceNodeId = srcId,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            hitMargin = hitMargin
        )
        this.hoveredTargetNodeId = target?.id
    }

    fun finishDrag(
        existingConnections: List<FlowConnection>,
        nodes: List<FlowNode>,
        workflowId: String
    ): DragConnectionResult {
        if (!isDragging) return DragConnectionResult.Cancelled

        val srcId = sourceNodeId
        val srcPort = sourcePortId ?: "out_default"
        val tgtId = hoveredTargetNodeId

        cancelDrag()

        if (srcId == null || tgtId == null) {
            return DragConnectionResult.Missed
        }

        val validation = WireConnectionEngine.validateConnection(
            fromNodeId = srcId,
            toNodeId = tgtId,
            existingConnections = existingConnections,
            nodes = nodes
        )

        return when (validation) {
            is ConnectionValidationResult.Valid -> {
                val conn = FlowConnection(
                    id = "conn_${UUID.randomUUID().toString().take(8)}",
                    workflowId = workflowId,
                    fromNodeId = srcId,
                    fromPortId = srcPort,
                    toNodeId = tgtId,
                    toPortId = validation.toNode.inputs.firstOrNull()?.id ?: "in_default"
                )
                DragConnectionResult.Success(conn, validation.toNode)
            }
            else -> DragConnectionResult.Rejected(validation)
        }
    }

    fun cancelDrag() {
        this.isDragging = false
        this.sourceNodeId = null
        this.sourcePortId = null
        this.hoveredTargetNodeId = null
    }
}
