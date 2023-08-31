package io.github.u.ways

import io.github.u.ways.SharedDbClient.config
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
import io.github.u.ways.util.runBlockingWithTimeoutUnit
import io.github.u.ways.util.testDatabaseClientConfiguration
import io.github.u.ways.util.withoutPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.await
import java.time.Duration
import java.util.stream.Stream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(PER_CLASS)
@ExtendWith(VertxExtension::class)
internal class SharedDbClientTest {
    private val appName = "test"

    private val underTestAppProps =
        JsonObject()
            .put(DATABASE_URL_ENV_KEY, testDatabaseClientConfiguration().url)
            .put(DATABASE_USERNAME_ENV_KEY, testDatabaseClientConfiguration().user)
            .put(DATABASE_PASSWORD_ENV_KEY, testDatabaseClientConfiguration().password)
            .put(DATABASE_MAX_POOL_CONNECTION_SIZE(appName), testDatabaseClientConfiguration().maxPoolSize)
            .put(DATABASE_CONNECTION_TIMEOUT_MS(appName), testDatabaseClientConfiguration().connectionTimeoutInMs)
            .put(DATABASE_CONNECTION_INIT_SCRIPT(appName), testDatabaseClientConfiguration().connectionInitSql)

    private val expectedSharedDbClientConfig = JsonObject()
        .put(DATABASE_URL_KEY, testDatabaseClientConfiguration().url)
        .put(DATABASE_USER_KEY, testDatabaseClientConfiguration().user)
        .put(DATABASE_PASSWORD_KEY, testDatabaseClientConfiguration().password)
        .put(DATABASE_MAX_POOL_SIZE_KEY, testDatabaseClientConfiguration().maxPoolSize)
        .put(DATABASE_CONNECTION_TIMEOUT_IN_MS_KEY, testDatabaseClientConfiguration().connectionTimeoutInMs)
        .put(DATABASE_CONNECTION_INIT_SQL_KEY, testDatabaseClientConfiguration().connectionInitSql)

    private fun configStringsToRemove(): Stream<Arguments> = Stream.of(
        Arguments.of(DATABASE_URL_ENV_KEY),
        Arguments.of(DATABASE_USERNAME_ENV_KEY),
        Arguments.of(DATABASE_PASSWORD_ENV_KEY),
        Arguments.of(DATABASE_MAX_POOL_CONNECTION_SIZE(appName)),
        Arguments.of(DATABASE_CONNECTION_TIMEOUT_MS(appName)),
        Arguments.of(DATABASE_CONNECTION_INIT_SCRIPT(appName))
    )

    @ParameterizedTest
    @MethodSource("configStringsToRemove")
    fun failsWhenAppPropsHaveMissingDbConfigs(configPathToRemove: String, vertx: Vertx) =
        runBlockingWithTimeoutUnit {
            shouldThrow<IllegalStateException> {
                SharedDbClient
                    .init(vertx, appName, underTestAppProps.withoutPath(configPathToRemove))
            }
        }

    @Test
    fun exposeSharedDbClientConfigsAsAJsonObject(vertx: Vertx) {
        runBlockingWithTimeoutUnit {
            SharedDbClient
                .init(vertx, appName, underTestAppProps)
                .config shouldBe expectedSharedDbClientConfig
        }
    }

    @Test
    fun shouldNotAllowTheModificationOfAJsonObject(vertx: Vertx): Unit =
        runBlockingWithTimeoutUnit {
            SharedDbClient
                .init(vertx, appName, underTestAppProps)
                .config
                .put(DATABASE_URL_KEY, "MODIFIED VALUE")
                .remove(DATABASE_PASSWORD_KEY)

            config shouldBe expectedSharedDbClientConfig
        }

    @Test
    fun shouldProvideAWayToCloseDataSource(vertx: Vertx) =
        runBlockingWithTimeoutUnit(duration = Duration.ofSeconds(15)) {
            SharedDbClient
                .init(vertx, appName, underTestAppProps).apply {
                pool.connection.await()
                close()

                shouldThrowAny { pool.connection.await() }.message shouldContain "Client is closed"
            }
        }
}