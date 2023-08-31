package io.github.u.ways.util

import io.vertx.core.json.JsonObject

internal fun JsonObject.withoutPath(path: String) =
    applyOnParent(path) { leafKey -> remove(leafKey) }

private fun JsonObject.applyOnParent(path: String, block: JsonObject.(leafKey: String) -> Unit) =
    path.split("/").let { keys ->
        copy().apply {
            keys.take(keys.size - 1).fold(this) { acc, key -> acc.getJsonObject(key) }.block(keys.last())
        }
    }