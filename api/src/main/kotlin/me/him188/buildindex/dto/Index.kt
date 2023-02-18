@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.buildindex.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
public data class Module(
    val id: UUID,
    val name: String,
)

@Serializable
public data class Branch(
    val id: UUID,
    val moduleId: UUID,
    val moduleName: String,
    val name: String,
    val latestIndexId: UUID?,
)

@Serializable
public data class Index(
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
public class User(
    public val id: UUID,
    public val username: String,
)