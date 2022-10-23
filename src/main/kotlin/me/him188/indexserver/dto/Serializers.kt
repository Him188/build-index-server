package me.him188.indexserver.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*


object UuidAsStringSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(String.serializer().deserialize(decoder))
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        String.serializer().serialize(encoder, value.toString())
    }
}