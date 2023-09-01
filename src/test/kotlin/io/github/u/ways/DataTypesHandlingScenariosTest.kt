package io.github.u.ways

import io.github.u.ways.config.DATABASE_CONNECTION_INIT_SCRIPT
import io.github.u.ways.config.DATABASE_CONNECTION_TIMEOUT_MS
import io.github.u.ways.config.DATABASE_MAX_POOL_CONNECTION_SIZE
import io.github.u.ways.config.DATABASE_PASSWORD_ENV_KEY
import io.github.u.ways.config.DATABASE_URL_ENV_KEY
import io.github.u.ways.config.DATABASE_USERNAME_ENV_KEY
import io.github.u.ways.util.runBlockingWithTimeoutUnit
import io.github.u.ways.util.testDatabaseClientConfiguration
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.templates.SqlTemplate
import java.nio.ByteBuffer
import java.time.Duration.ofSeconds
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class DataTypesHandlingScenariosTest {
    companion object {
        private const val APP_NAME = "test"
        private const val TEST_TABLE = "test_table"

        private const val TEST_STRING_COLUMN_KEY = "test_string_column"
        private const val TEST_UUID_COLUMN_KEY = "test_uuid_column"
        private const val TEST_TIMESTAMP_COLUMN_KEY = "test_timestamp_column"

        private const val STRING_KEY = "testString"
        private const val UUID_KEY = "testUuid"
        private const val TIMESTAMP_KEY = "testTimestamp"
    }

    @BeforeEach
    fun setUp(vertx: Vertx) = runBlockingWithTimeoutUnit {
        val configuration = testDatabaseClientConfiguration()

        SharedDbClient.init(
            vertx, APP_NAME, JsonObject()
                .put(DATABASE_URL_ENV_KEY, configuration.url)
                .put(DATABASE_USERNAME_ENV_KEY, configuration.user)
                .put(DATABASE_PASSWORD_ENV_KEY, configuration.password)
                .put(DATABASE_MAX_POOL_CONNECTION_SIZE(APP_NAME), configuration.maxPoolSize)
                .put(DATABASE_CONNECTION_TIMEOUT_MS(APP_NAME), configuration.connectionTimeoutInMs)
                .put(DATABASE_CONNECTION_INIT_SCRIPT(APP_NAME), configuration.connectionInitSql)
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
                    CREATE TABLE $TEST_TABLE (
                        $TEST_STRING_COLUMN_KEY    VARCHAR2(100),
                        $TEST_UUID_COLUMN_KEY      RAW(16),
                        $TEST_TIMESTAMP_COLUMN_KEY TIMESTAMP DEFAULT SYSTIMESTAMP
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

    /**
     * The only solution so far:
     * - Given our "UUID Byte -- VARCHAR2" test passed, we know that we can insert UUID byte array to VARCHAR2 column.
     * - So we can use RAWTOHEX to convert UUID byte array to VARCHAR2,
     * - Then we can use SUBSTR to split the VARCHAR2 into 5 parts,
     * - Afterwards we can use || to concatenate the 5 parts into a UUID string
     * - and then insert them into a VARCHAR2 column.
     *
     * Thus bypassing the ColumnDescriptor interrogation, but still inserting a UUID as a VARCHAR2 column.
     *
     * REF:
     * - https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/RAWTOHEX.html#GUID-F86E3B5B-7FEE-47FD-A0C2-2FC55DC21C9E
     * - https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SUBSTR.html#GUID-C8A20B57-C647-4649-A379-8651AA97187E
     */
    @Test
    fun `Solution - Converting the UUID as Byte, convert to RAWTOHEX and then format to UUID toString's BNF`() =
        runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
            val uuid = UUID.randomUUID()

            val connection = SharedDbClient.pool.connection.await()
            val template = """
                                INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) 
                                VALUES (
                                    SUBSTR(RAWTOHEX(#{$STRING_KEY}), 1, 8)  || '-' ||
                                    SUBSTR(RAWTOHEX(#{$STRING_KEY}), 9, 4)  || '-' ||
                                    SUBSTR(RAWTOHEX(#{$STRING_KEY}), 13, 4) || '-' ||
                                    SUBSTR(RAWTOHEX(#{$STRING_KEY}), 17, 4) || '-' ||
                                    SUBSTR(RAWTOHEX(#{$STRING_KEY}), 21)
                                )
                                """

            SqlTemplate
                .forUpdate(connection, template)
                .execute(mapOf(STRING_KEY to uuid.toByteArray()))
                .await()
                .rowCount() shouldBe 1

            val secondConnection = SharedDbClient.pool.connection.await()
            val selectTemplate = "SELECT $TEST_STRING_COLUMN_KEY FROM $TEST_TABLE"

            secondConnection.query(selectTemplate)
                .execute()
                .await()
                .firstOrNull()
                ?.getString(0)
                .shouldNotBeNull()
                .apply { UUID.fromString(this) shouldBe uuid }
        }

    @Nested
    inner class VARCHAR2 {

        @Test
        fun `String Random -- VARCHAR2`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (#{$STRING_KEY})"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to randomString(16)))
                    .await()
                    .rowCount() shouldBe 1
            }

        @Test
        fun `String Random -- VARCHAR2 -- CAST AS VARCHAR2(100)`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(#{$STRING_KEY} AS VARCHAR2(36)))"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to randomString(16)))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        @Test
        fun `UUID String -- VARCHAR2`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (#{$STRING_KEY})"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        @Test
        fun `UUID String -- VARCHAR2 -- CAST AS VARCHAR2(100)`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(#{$STRING_KEY} AS VARCHAR2(100)))"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        @Test
        fun `UUID String -- VARCHAR2 -- preparedQuery`() {
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                SharedDbClient.pool
                    .preparedQuery("INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (?)")
                    .execute(Tuple.of(UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }
        }

        // FIXME: FAILS WITH 4.3.5
        @Test
        fun `UUID String -- VARCHAR2 -- preparedQuery -- CAST AS VARCHAR2(100)`() {
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                SharedDbClient.pool
                    .preparedQuery("INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(? AS VARCHAR2(100)))")
                    .execute(Tuple.of(UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }
        }

        @Test
        fun `UUID Byte -- VARCHAR2 -- preparedQuery`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                SharedDbClient.pool
                    .preparedQuery("INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (?)")
                    .execute(Tuple.of(UUID.randomUUID().toByteArray()))
                    .await()
                    .rowCount() shouldBe 1
            }
    }

    @Nested
    inner class Raw {
        @Test
        fun `String Random -- RAW`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_UUID_COLUMN_KEY) VALUES (#{$UUID_KEY})"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(UUID_KEY to randomString(8).toByteArray()))
                    .await()
                    .rowCount() shouldBe 1
            }

        @Test
        fun `UUID Byte -- RAW`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_UUID_COLUMN_KEY) VALUES (#{$UUID_KEY})"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(UUID_KEY to UUID.randomUUID().toByteArray()))
                    .await()
                    .rowCount() shouldBe 1
            }
    }

    @Nested
    inner class Timestamp {
        @Test
        fun `Timestamp -- TIMESTAMP`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val templateInsert = "INSERT INTO $TEST_TABLE ($TEST_TIMESTAMP_COLUMN_KEY) VALUES (#{$TIMESTAMP_KEY})"

                val currentInstant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val timestampToInsert = java.sql.Timestamp.from(currentInstant)

                SqlTemplate
                    .forUpdate(connection, templateInsert)
                    .execute(mapOf(TIMESTAMP_KEY to timestampToInsert))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        @Test
        fun `Instant -- TIMESTAMP`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val templateInsert = "INSERT INTO $TEST_TABLE ($TEST_TIMESTAMP_COLUMN_KEY) VALUES (#{$TIMESTAMP_KEY})"

                SqlTemplate
                    .forUpdate(connection, templateInsert)
                    .execute(mapOf(TIMESTAMP_KEY to Instant.now().truncatedTo(ChronoUnit.MILLIS)))
                    .await()
                    .rowCount() shouldBe 1
            }

        @Test
        fun `LocalDateTime -- TIMESTAMP`() =
            runBlockingWithTimeoutUnit(ofSeconds(60), EmptyCoroutineContext) {
                val connection = SharedDbClient.pool.connection.await()
                val templateInsert = "INSERT INTO $TEST_TABLE ($TEST_TIMESTAMP_COLUMN_KEY) VALUES (#{$TIMESTAMP_KEY})"

                val currentInstant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val localDateTimeToInsert = java.time.LocalDateTime.ofInstant(currentInstant, java.time.ZoneId.systemDefault())

                SqlTemplate
                    .forUpdate(connection, templateInsert)
                    .execute(mapOf(TIMESTAMP_KEY to localDateTimeToInsert))
                    .await()
                    .rowCount() shouldBe 1
            }
    }

    private fun randomString(n: Int): String = (0..n)
        .map { ('a'..'z').random() }
        .joinToString("")

    private fun UUID.toByteArray(): ByteArray = ByteBuffer
        .wrap(ByteArray(16))
        .run {
            putLong(mostSignificantBits)
            putLong(leastSignificantBits)
        }.array()
}