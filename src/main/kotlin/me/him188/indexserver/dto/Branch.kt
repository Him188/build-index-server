@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.indexserver.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Branch(
    val id: UUID,
    val moduleId: UUID,
    val name: String,
    val latestIndex: UInt?,
)