package io.github.u.ways.config

import io.vertx.core.json.JsonObject

fun JsonObject.getConfigString(key: String) =
    checkNotNull(this.getString(key)) { "Missing config value for key: $key" }

fun JsonObject.getConfigInteger(key: String) =
    checkNotNull(this.getInteger(key)) { "Missing config value for key: $key" }

fun JsonObject.getConfigLong(key: String) =
    checkNotNull(this.getLong(key)) { "Missing config value for key: $key" }