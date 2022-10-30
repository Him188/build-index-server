@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.indexserver.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Module(
    val id: UUID,
    val name: String,
)

@Serializable
data class Branch(
    val id: UUID,
    val moduleId: UUID,
    val name: String,
    val latestIndexId: UUID?,
)

@Serializable
data class Index(
    val id: UUID,
    val branchId: UUID,
    val commitRef: String,
    val value: UInt,
    val date: LocalDateTime
) {
    init {
        require(commitRef.length == 40) { "Invalid commit ref: '$commitRef'" }
    }
}

@Serializable
class User(
    val id: UUID,
    val username: String,
)