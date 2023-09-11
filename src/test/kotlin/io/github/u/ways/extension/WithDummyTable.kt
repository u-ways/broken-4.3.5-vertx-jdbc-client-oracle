package io.github.u.ways.extension

import io.github.u.ways.SharedDbClient
import io.github.u.ways.config.DATABASE_CONNECTION_INIT_SCRIPT
import io.github.u.ways.config.DATABASE_CONNECTION_TIMEOUT_MS
import io.github.u.ways.config.DATABASE_MAX_POOL_CONNECTION_SIZE
import io.github.u.ways.config.DATABASE_PASSWORD_ENV_KEY
import io.github.u.ways.config.DATABASE_URL_ENV_KEY
import io.github.u.ways.config.DATABASE_USERNAME_ENV_KEY
import io.github.u.ways.util.runBlockingWithTimeoutUnit
import io.github.u.ways.util.testDatabaseClientConfiguration
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
open class WithDummyTable {
    companion object {
        internal const val APP_NAME = "test"
        internal const val TEST_TABLE = "test_table"

        internal const val TEST_STRING_COLUMN_KEY = "test_string_column"
        internal const val TEST_UUID_COLUMN_KEY = "test_uuid_column"
        internal const val TEST_TIMESTAMP_COLUMN_KEY = "test_timestamp_column"
    }

    @BeforeEach
    fun setUp(vertx: Vertx) = runBlockingWithTimeoutUnit {
        val configuration = testDatabaseClientConfiguration()

        SharedDbClient.init(
            vertx, APP_NAME, JsonObject()
                .put(DATABASE_URL_ENV_KEY, configuration.url)
                .put(DATABASE_USERNAME_ENV_KEY, configuration.user)
                .put(DATABASE_PASSWORD_ENV_KEY, configuration.password)
                .put(
                    DATABASE_MAX_POOL_CONNECTION_SIZE(APP_NAME),
                    configuration.maxPoolSize
                )
                .put(
                    DATABASE_CONNECTION_TIMEOUT_MS(APP_NAME),
                    configuration.connectionTimeoutInMs
                )
                .put(
                    DATABASE_CONNECTION_INIT_SCRIPT(APP_NAME),
                    configuration.connectionInitSql
                )
        )

        assertDoesNotThrow {
            SharedDbClient.pool
                .query(
                    """
                    -- We suppress 942 error codes as they indicate the table does not exist.
                    BEGIN
                        EXECUTE IMMEDIATE 'DROP TABLE TEST_TABLE';
                    EXCEPTION
                        WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
                    END;
                    """.trimIndent()
                )
                .execute()
                .await()
        }

        assertDoesNotThrow {
            SharedDbClient.pool
                .query(
                    """
                    CREATE TABLE ${TEST_TABLE} (
                        ${TEST_STRING_COLUMN_KEY}    VARCHAR2(100),
                        ${TEST_UUID_COLUMN_KEY}      RAW(16),
                        ${TEST_TIMESTAMP_COLUMN_KEY} TIMESTAMP DEFAULT SYSTIMESTAMP
                    )
                    """.trimIndent()
                )
                .execute()
                .await()
        }
    }

    @AfterEach
    fun tearDown() = runBlockingWithTimeoutUnit {
        SharedDbClient.close()
    }
}