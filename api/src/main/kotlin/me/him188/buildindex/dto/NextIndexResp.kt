@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.buildindex.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
public data class NextIndexResp(
    val moduleId: UUID,
    val branchId: UUID,
    val previousIndexId: UUID?,
    val previousIndexValue: UInt?,
    val newIndex: Index
)