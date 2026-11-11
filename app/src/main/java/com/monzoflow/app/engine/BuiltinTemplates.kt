package com.monzoflow.app.engine

import com.monzoflow.app.data.model.*
import java.util.UUID

object BuiltinTemplates {

    fun createDefaultWorkflows(): List<Triple<Workflow, List<FlowNode>, List<FlowConnection>>> {
        return listOf(
            createCoffeeSaverWorkflow(),
            createBigSpendSentinelWorkflow(),
            createSalarySplitterWorkflow()
        )
    }

    /**
     * 1. Coffee Spare-Change Saver:
     * When buying coffee -> round up to £1 -> push feed notification -> deposit to pot.
     */
    fun createCoffeeSaverWorkflow(): Triple<Workflow, List<FlowNode>, List<FlowConnection>> {
        val wfId = "wf_coffee_saver"
        val wf = Workflow(
            id = wfId,
            name = "☕ Coffee Penny Saver",
            description = "Rounds up Pret & Starbucks coffee purchases and pushes a reward card to your Monzo feed.",
            active = true
        )

        val triggerNode = FlowNode(
            id = "node_trigger_1",
            workflowId = wfId,
            type = NodeType.TRIGGER_WEBHOOK,
            category = NodeCategory.TRIGGER,
            title = "Monzo Webhook",
            subtitle = "transaction.created",
            posX = 60f,
            posY = 140f
        )

        val filterNode = FlowNode(
            id = "node_filter_1",
            workflowId = wfId,
            type = NodeType.LOGIC_FILTER,
            category = NodeCategory.LOGIC,
            title = "Coffee Filter",
            subtitle = "eating_out or Cafe",
            posX = 320f,
            posY = 140f,
            config = NodeConfig(
                filterCategory = "eating_out",
                filterComparison = "GREATER_THAN",
                filterMinAmountPence = 100L
            )
        )

        val roundupNode = FlowNode(
            id = "node_roundup_1",
            workflowId = wfId,
            type = NodeType.TRANSFORM_ROUNDUP,
            category = NodeCategory.TRANSFORM,
            title = "Spare Change",
            subtitle = "Round to £1.00",
            posX = 600f,
            posY = 100f,
            config = NodeConfig(roundUpTargetPence = 100)
        )

        val feedNode = FlowNode(
            id = "node_feed_1",
            workflowId = wfId,
            type = NodeType.MONZO_PUSH_FEED,
            category = NodeCategory.MONZO_ACTION,
            title = "Push Monzo Feed",
            subtitle = "In-App Card",
            posX = 880f,
            posY = 100f,
            config = NodeConfig(
                feedTitleTemplate = "☕ Coffee Penny Saved!",
                feedBodyTemplate = "Saved {{roundup_formatted}} spare change from your {{amount}} purchase at {{merchant}}.",
                feedBackgroundColor = "#FF4D4D"
            )
        )

        val potNode = FlowNode(
            id = "node_pot_1",
            workflowId = wfId,
            type = NodeType.MONZO_POT_DEPOSIT,
            category = NodeCategory.MONZO_ACTION,
            title = "Deposit to Pot",
            subtitle = "Coffee Saver Pot",
            posX = 1160f,
            posY = 100f,
            config = NodeConfig(
                potAmountType = "ROUNDUP_SPARE",
                potName = "Coffee Savings"
            )
        )

        val connections = listOf(
            FlowConnection("conn_1", wfId, triggerNode.id, "out_default", filterNode.id, "in_default"),
            FlowConnection("conn_2", wfId, filterNode.id, "out_true", roundupNode.id, "in_default"),
            FlowConnection("conn_3", wfId, roundupNode.id, "out_default", feedNode.id, "in_default"),
            FlowConnection("conn_4", wfId, feedNode.id, "out_default", potNode.id, "in_default")
        )

        return Triple(wf, listOf(triggerNode, filterNode, roundupNode, feedNode, potNode), connections)
    }

    /**
     * 2. Big Spend Sentinel:
     * When transaction > £50.00 -> push Monzo feed alert + Android notification.
     */
    fun createBigSpendSentinelWorkflow(): Triple<Workflow, List<FlowNode>, List<FlowConnection>> {
        val wfId = "wf_big_spend_sentinel"
        val wf = Workflow(
            id = wfId,
            name = "🚨 Big Spend Sentinel",
            description = "Triggers high-priority in-app and phone alerts whenever a purchase exceeds £50.00.",
            active = true
        )

        val trigger = FlowNode(
            id = "node_t2_1",
            workflowId = wfId,
            type = NodeType.TRIGGER_WEBHOOK,
            category = NodeCategory.TRIGGER,
            title = "Monzo Webhook",
            subtitle = "Live transactions",
            posX = 60f,
            posY = 140f
        )

        val filter = FlowNode(
            id = "node_t2_2",
            workflowId = wfId,
            type = NodeType.LOGIC_FILTER,
            category = NodeCategory.LOGIC,
            title = "Spend > £50.00",
            subtitle = "Amount check",
            posX = 320f,
            posY = 140f,
            config = NodeConfig(
                filterComparison = "GREATER_THAN",
                filterMinAmountPence = 5000L
            )
        )

        val feed = FlowNode(
            id = "node_t2_3",
            workflowId = wfId,
            type = NodeType.MONZO_PUSH_FEED,
            category = NodeCategory.MONZO_ACTION,
            title = "Push Monzo Feed",
            subtitle = "Warning Banner",
            posX = 600f,
            posY = 140f,
            config = NodeConfig(
                feedTitleTemplate = "🚨 High Spend Alert",
                feedBodyTemplate = "Large transaction of {{amount}} at {{merchant}}.",
                feedBackgroundColor = "#FFB300"
            )
        )

        val notify = FlowNode(
            id = "node_t2_4",
            workflowId = wfId,
            type = NodeType.NOTIFY_ANDROID,
            category = NodeCategory.NOTIFICATION,
            title = "Phone Alert",
            subtitle = "System Notification",
            posX = 880f,
            posY = 140f,
            config = NodeConfig(
                notifyTitle = "MonzoFlow: {{amount}} Spent",
                notifyMessage = "High spend detected at {{merchant}}"
            )
        )

        val connections = listOf(
            FlowConnection("conn_b1", wfId, trigger.id, "out_default", filter.id, "in_default"),
            FlowConnection("conn_b2", wfId, filter.id, "out_true", feed.id, "in_default"),
            FlowConnection("conn_b3", wfId, feed.id, "out_default", notify.id, "in_default")
        )

        return Triple(wf, listOf(trigger, filter, feed, notify), connections)
    }

    /**
     * 3. Salary Splitter:
     * When salary is received (>£1,000) -> calculate 20% -> annotate transaction -> push feed.
     */
    fun createSalarySplitterWorkflow(): Triple<Workflow, List<FlowNode>, List<FlowConnection>> {
        val wfId = "wf_salary_splitter"
        val wf = Workflow(
            id = wfId,
            name = "💰 Salary Sorter & Splitter",
            description = "Auto-allocates 20% into savings and tags transaction with #salary.",
            active = true
        )

        val trigger = FlowNode(
            id = "node_s1",
            workflowId = wfId,
            type = NodeType.TRIGGER_WEBHOOK,
            category = NodeCategory.TRIGGER,
            title = "Monzo Webhook",
            subtitle = "Income events",
            posX = 60f,
            posY = 140f
        )

        val filter = FlowNode(
            id = "node_s2",
            workflowId = wfId,
            type = NodeType.LOGIC_FILTER,
            category = NodeCategory.LOGIC,
            title = "Salary Filter",
            subtitle = "Income > £1000",
            posX = 320f,
            posY = 140f,
            config = NodeConfig(
                filterComparison = "GREATER_THAN",
                filterMinAmountPence = 100000L
            )
        )

        val annotate = FlowNode(
            id = "node_s3",
            workflowId = wfId,
            type = NodeType.MONZO_ANNOTATE,
            category = NodeCategory.MONZO_ACTION,
            title = "Annotate Monzo",
            subtitle = "Add #salary tag",
            posX = 600f,
            posY = 140f,
            config = NodeConfig(
                annotateNotesTemplate = "Monthly Salary Auto-Sorted by MonzoFlow",
                annotateTags = "#salary #income"
            )
        )

        val feed = FlowNode(
            id = "node_s4",
            workflowId = wfId,
            type = NodeType.MONZO_PUSH_FEED,
            category = NodeCategory.MONZO_ACTION,
            title = "Push Monzo Feed",
            subtitle = "Payday Card",
            posX = 880f,
            posY = 140f,
            config = NodeConfig(
                feedTitleTemplate = "🎉 Payday Processed!",
                feedBodyTemplate = "Income of {{amount}} received from {{merchant}}. Automations executed.",
                feedBackgroundColor = "#00D2A0"
            )
        )

        val connections = listOf(
            FlowConnection("conn_s1", wfId, trigger.id, "out_default", filter.id, "in_default"),
            FlowConnection("conn_s2", wfId, filter.id, "out_true", annotate.id, "in_default"),
            FlowConnection("conn_s3", wfId, annotate.id, "out_default", feed.id, "in_default")
        )

        return Triple(wf, listOf(trigger, filter, annotate, feed), connections)
    }
}
