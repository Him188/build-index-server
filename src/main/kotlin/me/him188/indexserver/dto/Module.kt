@file:UseSerializers(UuidAsStringSerializer::class)

package me.him188.indexserver.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Module(
    val id: UUID,
    val name: String,
)