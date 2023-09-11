package io.github.u.ways.bugs

import io.github.u.ways.SharedDbClient
import io.github.u.ways.extension.WithDummyTable
import io.github.u.ways.util.randomString
import io.github.u.ways.util.runBlockingWithTimeoutUnit
import io.github.u.ways.utils.adaptToVertxBuffer
import io.github.u.ways.utils.toByteArray
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.templates.SqlTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DataTypesHandlingScenariosTest : WithDummyTable() {
    companion object {
        private const val STRING_KEY = "testString"
        private const val UUID_KEY = "testUuid"
        private const val TIMESTAMP_KEY = "testTimestamp"
    }

    /**
     * The cleanest solution so far wrap the UUID in a Vertx Buffer.
     */
    @Test
    fun `Solution - Wrapping the UUID in a Vertx Buffer`() =
        runBlockingWithTimeoutUnit {
            val connection = SharedDbClient.pool.connection.await()
            val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(#{$STRING_KEY} AS VARCHAR2(100)))"

            SqlTemplate
                .forUpdate(connection, template)
                .execute(mapOf(STRING_KEY to UUID.randomUUID().adaptToVertxBuffer()))
                .await()
                .rowCount() shouldBe 1
        }


    /**
     * Another (ugly) solution::
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
        runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(#{$STRING_KEY} AS VARCHAR2(36)))"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to randomString(16)))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        /*
            java.sql.SQLException: Invalid column type
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectCritical(OraclePreparedStatement.java:8782)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:8264)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:9039)
                at oracle.jdbc.driver.OraclePreparedStatement.setObject(OraclePreparedStatement.java:9014)
                at oracle.jdbc.driver.OraclePreparedStatementWrapper.setObject(OraclePreparedStatementWrapper.java:221)
                at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.setObject(HikariProxyPreparedStatement.java)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.fillStatement(JDBCPreparedQuery.java:142)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:63)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:44)
                at io.vertx.ext.jdbc.impl.JDBCConnectionImpl.lambda$schedule$3(JDBCConnectionImpl.java:219)
                at io.vertx.core.impl.ContextBase.lambda$null$0(ContextBase.java:137)
                at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:264)
                at io.vertx.core.impl.ContextBase.lambda$executeBlocking$1(ContextBase.java:135)
                at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76)
                at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
                at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
                at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
                at java.base/java.lang.Thread.run(Thread.java:833)
         */
        @Test
        fun `UUID String -- VARCHAR2`() =
            runBlockingWithTimeoutUnit {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (#{$STRING_KEY})"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        /*
            java.sql.SQLException: Invalid column type
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectCritical(OraclePreparedStatement.java:8782)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:8264)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:9039)
                at oracle.jdbc.driver.OraclePreparedStatement.setObject(OraclePreparedStatement.java:9014)
                at oracle.jdbc.driver.OraclePreparedStatementWrapper.setObject(OraclePreparedStatementWrapper.java:221)
                at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.setObject(HikariProxyPreparedStatement.java)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.fillStatement(JDBCPreparedQuery.java:142)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:63)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:44)
                at io.vertx.ext.jdbc.impl.JDBCConnectionImpl.lambda$schedule$3(JDBCConnectionImpl.java:219)
                at io.vertx.core.impl.ContextBase.lambda$null$0(ContextBase.java:137)
                at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:264)
                at io.vertx.core.impl.ContextBase.lambda$executeBlocking$1(ContextBase.java:135)
                at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76)
                at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
                at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
                at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
                at java.base/java.lang.Thread.run(Thread.java:833)
         */
        @Test
        fun `UUID String -- VARCHAR2 -- CAST AS VARCHAR2(100)`() =
            runBlockingWithTimeoutUnit {
                val connection = SharedDbClient.pool.connection.await()
                val template = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(#{$STRING_KEY} AS VARCHAR2(100)))"

                SqlTemplate
                    .forUpdate(connection, template)
                    .execute(mapOf(STRING_KEY to UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }

        // FIXME: FAILS WITH 4.3.5
        /*
        java.sql.SQLException: Invalid column type
            at oracle.jdbc.driver.OraclePreparedStatement.setObjectCritical(OraclePreparedStatement.java:8782)
            at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:8264)
            at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:9039)
            at oracle.jdbc.driver.OraclePreparedStatement.setObject(OraclePreparedStatement.java:9014)
            at oracle.jdbc.driver.OraclePreparedStatementWrapper.setObject(OraclePreparedStatementWrapper.java:221)
            at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.setObject(HikariProxyPreparedStatement.java)
            at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.fillStatement(JDBCPreparedQuery.java:142)
            at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:63)
            at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:44)
            at io.vertx.ext.jdbc.impl.JDBCConnectionImpl.lambda$schedule$3(JDBCConnectionImpl.java:219)
            at io.vertx.core.impl.ContextBase.lambda$null$0(ContextBase.java:137)
            at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:264)
            at io.vertx.core.impl.ContextBase.lambda$executeBlocking$1(ContextBase.java:135)
            at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76)
            at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
            at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
            at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
            at java.base/java.lang.Thread.run(Thread.java:833)
         */
        @Test
        fun `UUID String -- VARCHAR2 -- preparedQuery`() {
            runBlockingWithTimeoutUnit {
                SharedDbClient.pool
                    .preparedQuery("INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (?)")
                    .execute(Tuple.of(UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }
        }

        // FIXME: FAILS WITH 4.3.5
        /*
            java.sql.SQLException: Invalid column type
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectCritical(OraclePreparedStatement.java:8782)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:8264)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:9039)
                at oracle.jdbc.driver.OraclePreparedStatement.setObject(OraclePreparedStatement.java:9014)
                at oracle.jdbc.driver.OraclePreparedStatementWrapper.setObject(OraclePreparedStatementWrapper.java:221)
                at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.setObject(HikariProxyPreparedStatement.java)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.fillStatement(JDBCPreparedQuery.java:142)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:63)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:44)
                at io.vertx.ext.jdbc.impl.JDBCConnectionImpl.lambda$schedule$3(JDBCConnectionImpl.java:219)
                at io.vertx.core.impl.ContextBase.lambda$null$0(ContextBase.java:137)
                at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:264)
                at io.vertx.core.impl.ContextBase.lambda$executeBlocking$1(ContextBase.java:135)
                at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76)
                at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
                at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
                at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
                at java.base/java.lang.Thread.run(Thread.java:833)
         */
        @Test
        fun `UUID String -- VARCHAR2 -- preparedQuery -- CAST AS VARCHAR2(100)`() {
            runBlockingWithTimeoutUnit {
                SharedDbClient.pool
                    .preparedQuery("INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (CAST(? AS VARCHAR2(100)))")
                    .execute(Tuple.of(UUID.randomUUID().toString()))
                    .await()
                    .rowCount() shouldBe 1
            }
        }

        @Test
        fun `UUID Byte -- VARCHAR2 -- preparedQuery`() =
            runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
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
        /*
            java.sql.SQLException: Invalid column type
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectCritical(OraclePreparedStatement.java:8782)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:8264)
                at oracle.jdbc.driver.OraclePreparedStatement.setObjectInternal(OraclePreparedStatement.java:9039)
                at oracle.jdbc.driver.OraclePreparedStatement.setObject(OraclePreparedStatement.java:9014)
                at oracle.jdbc.driver.OraclePreparedStatementWrapper.setObject(OraclePreparedStatementWrapper.java:221)
                at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.setObject(HikariProxyPreparedStatement.java)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.fillStatement(JDBCPreparedQuery.java:142)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:63)
                at io.vertx.jdbcclient.impl.actions.JDBCPreparedQuery.execute(JDBCPreparedQuery.java:44)
                at io.vertx.ext.jdbc.impl.JDBCConnectionImpl.lambda$schedule$3(JDBCConnectionImpl.java:219)
                at io.vertx.core.impl.ContextBase.lambda$null$0(ContextBase.java:137)
                at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:264)
                at io.vertx.core.impl.ContextBase.lambda$executeBlocking$1(ContextBase.java:135)
                at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76)
                at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
                at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
                at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
                at java.base/java.lang.Thread.run(Thread.java:833)
         */
        @Test
        fun `Instant -- TIMESTAMP`() =
            runBlockingWithTimeoutUnit {
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
            runBlockingWithTimeoutUnit {
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
}