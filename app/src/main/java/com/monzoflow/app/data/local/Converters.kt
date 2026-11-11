package com.monzoflow.app.data.local

import androidx.room.TypeConverter
import com.monzoflow.app.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @TypeConverter
    fun fromNodeConfig(config: NodeConfig): String = json.encodeToString(config)

    @TypeConverter
    fun toNodeConfig(str: String): NodeConfig = try {
        json.decodeFromString(str)
    } catch (e: Exception) {
        NodeConfig()
    }

    @TypeConverter
    fun fromPortList(ports: List<NodePort>): String = json.encodeToString(ports)

    @TypeConverter
    fun toPortList(str: String): List<NodePort> = try {
        json.decodeFromString(str)
    } catch (e: Exception) {
        emptyList()
    }

    @TypeConverter
    fun fromExecutionSteps(steps: List<NodeExecutionStep>): String = json.encodeToString(steps)

    @TypeConverter
    fun toExecutionSteps(str: String): List<NodeExecutionStep> = try {
        json.decodeFromString(str)
    } catch (e: Exception) {
        emptyList()
    }
}
