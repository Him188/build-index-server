@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.indexserver.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class NextIndexResp(
    val moduleId: UUID,
    val branchId: UUID,
    val previousIndexId: UUID?,
    val previousIndexValue: UInt?,
    val newIndex: Index
)