@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.indexserver.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Index(
    val id: UUID,
    val branchId: UUID,
    val commitRef: String,
    val index: UInt,
    val date: LocalDateTime
) {
    init {
        require(commitRef.length == 40) { "Invalid commit ref: '$commitRef'" }
    }
}