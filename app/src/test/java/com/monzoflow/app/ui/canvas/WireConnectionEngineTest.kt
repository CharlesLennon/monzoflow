package com.monzoflow.app.ui.canvas

import com.monzoflow.app.data.model.FlowConnection
import com.monzoflow.app.data.model.FlowNode
import com.monzoflow.app.data.model.NodeType
import org.junit.Assert.*
import org.junit.Test

class WireConnectionEngineTest {

    @Test
    fun testScreenToCanvasAndBack() {
        val panX = 50f
        val panY = -20f
        val scale = 1.5f

        val canvasX = 100f
        val canvasY = 200f

        val screenX = WireConnectionEngine.canvasToScreenX(canvasX, panX, scale)
        val screenY = WireConnectionEngine.canvasToScreenY(canvasY, panY, scale)

        assertEquals(200f, screenX, 0.001f)
        assertEquals(280f, screenY, 0.001f)

        val recoveredCanvasX = WireConnectionEngine.screenToCanvasX(screenX, panX, scale)
        val recoveredCanvasY = WireConnectionEngine.screenToCanvasY(screenY, panY, scale)

        assertEquals(canvasX, recoveredCanvasX, 0.001f)
        assertEquals(canvasY, recoveredCanvasY, 0.001f)
    }

    @Test
    fun testPortCoordinates() {
        val nodeX = 100f
        val nodeY = 200f
        val cardWidth = 220f
        val cardHeight = 95f
        val scale = 1.0f
        val panX = 0f
        val panY = 0f

        val outputPos = WireConnectionEngine.calculateOutputPortPos(
            nodeX = nodeX,
            nodeY = nodeY,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            panX = panX,
            panY = panY,
            scale = scale
        )

        assertEquals(310f, outputPos.x, 0.001f) // 100 + 220 - 10
        assertEquals(280.75f, outputPos.y, 0.001f) // 200 + 95 * 0.85

        val inputPos = WireConnectionEngine.calculateInputPortPos(
            nodeX = 400f,
            nodeY = 200f,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            panX = panX,
            panY = panY,
            scale = scale
        )

        assertEquals(410f, inputPos.x, 0.001f) // 400 + 10
        assertEquals(280.75f, inputPos.y, 0.001f) // 200 + 95 * 0.85
    }

    @Test
    fun testHitTestingTargetNode() {
        val sourceNode = FlowNode.create(NodeType.TRIGGER_WEBHOOK, 100f, 100f, "wf_1")
        val targetNode = FlowNode.create(NodeType.LOGIC_FILTER, 400f, 100f, "wf_1")
        val triggerNoInputs = FlowNode.create(NodeType.TRIGGER_MONEY_RECEIVED, 700f, 100f, "wf_1")

        val nodes = listOf(sourceNode, targetNode, triggerNoInputs)
        val positions = mapOf(
            sourceNode.id to WirePoint(100f, 100f),
            targetNode.id to WirePoint(400f, 100f),
            triggerNoInputs.id to WirePoint(700f, 100f)
        )

        // 1. Hovering directly inside target node bounds (400..620, 100..195)
        val hit1 = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = WirePoint(450f, 130f),
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = sourceNode.id,
            cardWidth = 220f,
            cardHeight = 95f,
            hitMargin = 20f
        )
        assertNotNull(hit1)
        assertEquals(targetNode.id, hit1?.id)

        // 2. Hovering in generous hit margin around target node (e.g. x = 385, y = 90)
        val hit2 = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = WirePoint(385f, 90f),
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = sourceNode.id,
            cardWidth = 220f,
            cardHeight = 95f,
            hitMargin = 20f
        )
        assertNotNull(hit2)
        assertEquals(targetNode.id, hit2?.id)

        // 3. Hovering over source node itself - MUST NOT TARGET SELF
        val hitSelf = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = WirePoint(150f, 130f),
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = sourceNode.id,
            cardWidth = 220f,
            cardHeight = 95f,
            hitMargin = 20f
        )
        assertNull(hitSelf)

        // 4. Hovering over another trigger node that has NO inputs - MUST NOT HIT
        val hitTrigger = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = WirePoint(750f, 130f),
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = sourceNode.id,
            cardWidth = 220f,
            cardHeight = 95f,
            hitMargin = 20f
        )
        assertNull(hitTrigger)

        // 5. Hovering in empty canvas space - MISS
        val hitEmpty = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = WirePoint(10f, 10f),
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = sourceNode.id,
            cardWidth = 220f,
            cardHeight = 95f,
            hitMargin = 20f
        )
        assertNull(hitEmpty)
    }

    @Test
    fun testValidateConnection() {
        val nodeA = FlowNode.create(NodeType.TRIGGER_WEBHOOK, 100f, 100f, "wf_1")
        val nodeB = FlowNode.create(NodeType.LOGIC_FILTER, 400f, 100f, "wf_1")
        val nodeC = FlowNode.create(NodeType.MONZO_PUSH_FEED, 700f, 100f, "wf_1")
        val triggerNoInputs = FlowNode.create(NodeType.TRIGGER_MONEY_RECEIVED, 1000f, 100f, "wf_1")

        val nodes = listOf(nodeA, nodeB, nodeC, triggerNoInputs)
        val existingConns = mutableListOf(
            FlowConnection(
                id = "conn_1",
                workflowId = "wf_1",
                fromNodeId = nodeA.id,
                fromPortId = "out_default",
                toNodeId = nodeB.id,
                toPortId = "in_default"
            )
        )

        // 1. Valid new connection: B -> C
        val validResult = WireConnectionEngine.validateConnection(
            fromNodeId = nodeB.id,
            toNodeId = nodeC.id,
            existingConnections = existingConns,
            nodes = nodes
        )
        assertTrue(validResult is ConnectionValidationResult.Valid)

        // 2. Reject self connection: A -> A
        val selfResult = WireConnectionEngine.validateConnection(
            fromNodeId = nodeA.id,
            toNodeId = nodeA.id,
            existingConnections = existingConns,
            nodes = nodes
        )
        assertTrue(selfResult is ConnectionValidationResult.SelfConnection)

        // 3. Reject duplicate connection: A -> B already exists
        val duplicateResult = WireConnectionEngine.validateConnection(
            fromNodeId = nodeA.id,
            toNodeId = nodeB.id,
            existingConnections = existingConns,
            nodes = nodes
        )
        assertTrue(duplicateResult is ConnectionValidationResult.AlreadyConnected)

        // 4. Reject connecting to a target with no inputs: B -> triggerNoInputs
        val noInputsResult = WireConnectionEngine.validateConnection(
            fromNodeId = nodeB.id,
            toNodeId = triggerNoInputs.id,
            existingConnections = existingConns,
            nodes = nodes
        )
        assertTrue(noInputsResult is ConnectionValidationResult.TargetHasNoInputs)
    }

    @Test
    fun testDragSessionLifecycle() {
        val nodeA = FlowNode.create(NodeType.TRIGGER_WEBHOOK, 100f, 100f, "wf_1")
        val nodeB = FlowNode.create(NodeType.LOGIC_FILTER, 400f, 100f, "wf_1")
        val nodes = listOf(nodeA, nodeB)
        val positions = mapOf(
            nodeA.id to WirePoint(100f, 100f),
            nodeB.id to WirePoint(400f, 100f)
        )

        val session = WireDragSession()
        assertFalse(session.isDragging)

        // Start drag from Node A
        session.startDrag(
            fromNodeId = nodeA.id,
            fromPortId = "out_default",
            startCanvasPoint = WirePoint(310f, 180f),
            startScreenPoint = WirePoint(310f, 180f)
        )
        assertTrue(session.isDragging)
        assertEquals(nodeA.id, session.sourceNodeId)
        assertNull(session.hoveredTargetNodeId)

        // Drag finger toward empty space
        session.updateDrag(
            currentScreenPoint = WirePoint(200f, 200f),
            panX = 0f,
            panY = 0f,
            scale = 1.0f,
            nodes = nodes,
            nodePositions = positions
        )
        assertTrue(session.isDragging)
        assertNull(session.hoveredTargetNodeId)

        // Drag finger over Node B
        session.updateDrag(
            currentScreenPoint = WirePoint(420f, 120f),
            panX = 0f,
            panY = 0f,
            scale = 1.0f,
            nodes = nodes,
            nodePositions = positions
        )
        assertEquals(nodeB.id, session.hoveredTargetNodeId)

        // Finish drag on Node B
        val result = session.finishDrag(
            existingConnections = emptyList(),
            nodes = nodes,
            workflowId = "wf_1"
        )
        assertTrue("Expected successful connection result", result is DragConnectionResult.Success)
        val success = result as DragConnectionResult.Success
        assertEquals(nodeA.id, success.connection.fromNodeId)
        assertEquals(nodeB.id, success.connection.toNodeId)
        assertFalse("Session should no longer be dragging after finish", session.isDragging)
    }

    @Test
    fun testLiveWireMagneticSnapping() {
        val nodeA = FlowNode.create(NodeType.TRIGGER_WEBHOOK, 100f, 100f, "wf_1")
        val nodeB = FlowNode.create(NodeType.LOGIC_FILTER, 400f, 100f, "wf_1")
        val nodes = listOf(nodeA, nodeB)
        val positions = mapOf(
            nodeA.id to WirePoint(100f, 100f),
            nodeB.id to WirePoint(400f, 100f)
        )

        // When NO target is hovered, wire endpoint follows pointer exactly
        val pointerScreen = WirePoint(250f, 160f)
        val freeEndpoint = WireConnectionEngine.calculateLiveWireEndPoint(
            currentScreenPoint = pointerScreen,
            hoveredTargetNodeId = null,
            nodePositions = positions,
            nodes = nodes,
            panX = 0f,
            panY = 0f,
            scale = 1.0f
        )
        assertEquals(250f, freeEndpoint.x, 0.001f)
        assertEquals(160f, freeEndpoint.y, 0.001f)

        // When Node B is hovered, wire endpoint magnetically snaps to Node B's input port!
        // Input port for node at (400, 100) with scale 1.0, pan 0: x = 400 + 10 = 410, y = 100 + 95 * 0.85 = 180.75
        val snappedEndpoint = WireConnectionEngine.calculateLiveWireEndPoint(
            currentScreenPoint = pointerScreen,
            hoveredTargetNodeId = nodeB.id,
            nodePositions = positions,
            nodes = nodes,
            panX = 0f,
            panY = 0f,
            scale = 1.0f
        )
        assertEquals(410f, snappedEndpoint.x, 0.001f)
        assertEquals(180.75f, snappedEndpoint.y, 0.001f)
    }

    @Test
    fun testZoomAndPanHitTesting() {
        val nodeA = FlowNode.create(NodeType.TRIGGER_WEBHOOK, 100f, 100f, "wf_1")
        val nodeB = FlowNode.create(NodeType.LOGIC_FILTER, 400f, 100f, "wf_1")
        val nodes = listOf(nodeA, nodeB)
        val positions = mapOf(
            nodeA.id to WirePoint(100f, 100f),
            nodeB.id to WirePoint(400f, 100f)
        )

        // With scale = 2.0f, panX = 100f, panY = -50f
        // Node B in canvas is at (400, 100).
        // Screen coords for Node B: x = 400 * 2 + 100 = 900, y = 100 * 2 - 50 = 150.
        val pointerScreen = WirePoint(920f, 170f)
        val pointerCanvas = WireConnectionEngine.screenToCanvas(pointerScreen, panX = 100f, panY = -50f, scale = 2.0f)
        assertEquals(410f, pointerCanvas.x, 0.001f)
        assertEquals(110f, pointerCanvas.y, 0.001f)

        val hit = WireConnectionEngine.hitTestTargetNode(
            canvasPoint = pointerCanvas,
            nodes = nodes,
            nodePositions = positions,
            sourceNodeId = nodeA.id
        )
        assertNotNull("Should hit target node under zoom and pan", hit)
        assertEquals(nodeB.id, hit?.id)
    }
}
