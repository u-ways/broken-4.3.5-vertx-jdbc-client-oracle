package io.github.u.ways

import io.github.u.ways.config.DATABASE_CONNECTION_INIT_SCRIPT
import io.github.u.ways.config.DATABASE_CONNECTION_INIT_SQL_KEY
import io.github.u.ways.config.DATABASE_CONNECTION_TIMEOUT_IN_MS_KEY
import io.github.u.ways.config.DATABASE_CONNECTION_TIMEOUT_MS
import io.github.u.ways.config.DATABASE_MAX_POOL_CONNECTION_SIZE
import io.github.u.ways.config.DATABASE_MAX_POOL_SIZE_KEY
import io.github.u.ways.config.DATABASE_PASSWORD_ENV_KEY
import io.github.u.ways.config.DATABASE_PASSWORD_KEY
import io.github.u.ways.config.DATABASE_URL_ENV_KEY
import io.github.u.ways.config.DATABASE_URL_KEY
import io.github.u.ways.config.DATABASE_USERNAME_ENV_KEY
import io.github.u.ways.config.DATABASE_USER_KEY
import io.github.u.ways.config.getConfigInteger
import io.github.u.ways.config.getConfigLong
import io.github.u.ways.config.getConfigString
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.sqlclient.Pool

interface ConfiguredDbClient {
    val pool: Pool
    val config: JsonObject

    suspend fun close()

    fun appPropsToConfigData(appProps: JsonObject, appName: String): JsonObject = json {
        obj(
            DATABASE_URL_KEY to appProps.getConfigString(DATABASE_URL_ENV_KEY),
            DATABASE_USER_KEY to appProps.getConfigString(DATABASE_USERNAME_ENV_KEY),
            DATABASE_PASSWORD_KEY to appProps.getConfigString(DATABASE_PASSWORD_ENV_KEY),
            DATABASE_MAX_POOL_SIZE_KEY to appProps.getConfigInteger(DATABASE_MAX_POOL_CONNECTION_SIZE(appName)),
            DATABASE_CONNECTION_TIMEOUT_IN_MS_KEY to appProps.getConfigLong(DATABASE_CONNECTION_TIMEOUT_MS(appName)),
            DATABASE_CONNECTION_INIT_SQL_KEY to appProps.getConfigString(DATABASE_CONNECTION_INIT_SCRIPT(appName))
        )
    }

    fun configDataToJdbcPoolConfig(configData: JsonObject) = json {
        obj(
            "provider_class" to "io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider",
            "jdbcUrl" to configData.getString(DATABASE_URL_KEY),
            "username" to configData.getString(DATABASE_USER_KEY),
            "password" to configData.getString(DATABASE_PASSWORD_KEY),
            "maximumPoolSize" to configData.getInteger(DATABASE_MAX_POOL_SIZE_KEY),
            "connectionTimeout" to configData.getLong(DATABASE_CONNECTION_TIMEOUT_IN_MS_KEY),
            "connectionInitSql" to configData.getString(DATABASE_CONNECTION_INIT_SQL_KEY)
        )
    }
}