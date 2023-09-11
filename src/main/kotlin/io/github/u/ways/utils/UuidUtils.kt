package io.github.u.ways.utils

import io.vertx.core.buffer.Buffer
import java.nio.ByteBuffer
import java.util.UUID

fun UUID.toByteArray(): ByteArray = ByteBuffer
    .wrap(ByteArray(16))
    .run {
        putLong(mostSignificantBits)
        putLong(leastSignificantBits)
    }.array()

fun UUID.adaptToVertxBuffer(): Buffer =
    Buffer.buffer(this.toString())