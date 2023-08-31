package io.github.u.ways.config

private val DATABASE_DETAILS = { appName: String -> "$appName.database" }

val DATABASE_MAX_POOL_CONNECTION_SIZE = { appName: String -> "${DATABASE_DETAILS(appName)}.connection.max-pool-size" }
val DATABASE_CONNECTION_TIMEOUT_MS = { appName: String -> "${DATABASE_DETAILS(appName)}.connection.timeout-ms" }
val DATABASE_CONNECTION_INIT_SCRIPT = { appName: String -> "${DATABASE_DETAILS(appName)}.connection.init-script" }