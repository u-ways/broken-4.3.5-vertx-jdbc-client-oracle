package io.github.u.ways

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.Shareable
import io.vertx.jdbcclient.JDBCPool
import io.vertx.kotlin.coroutines.await

object SharedDbClient: Shareable, ConfiguredDbClient {
    private lateinit var instance: JDBCPool
    private var configData = JsonObject()

    override val pool: JDBCPool get() = instance
    override val config: JsonObject get() = configData.copy()

    fun init(vertx: Vertx, appName: String, appProps: JsonObject) = apply {
        configData = appPropsToConfigData(appProps, appName)
        instance = JDBCPool.pool(vertx, configDataToJdbcPoolConfig(configData))
    }

    override suspend fun close() {
        instance.close().await()
    }
}