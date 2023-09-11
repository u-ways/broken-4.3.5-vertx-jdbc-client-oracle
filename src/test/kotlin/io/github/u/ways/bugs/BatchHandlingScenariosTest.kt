package io.github.u.ways.bugs

import io.github.u.ways.SharedDbClient
import io.github.u.ways.connection.Query.batchQuery
import io.github.u.ways.connection.Query.multiInsertQuery
import io.github.u.ways.extension.WithDummyTable
import io.github.u.ways.util.randomString
import io.github.u.ways.util.runBlockingWithTimeoutUnit
import io.kotest.matchers.shouldBe
import io.vertx.kotlin.coroutines.await
import java.time.Duration.ofSeconds
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.jupiter.api.Test

class BatchHandlingScenariosTest: WithDummyTable() {

    // FIXME: FAILS WITH 4.4.5
    /*
        Failed to execute batch query: INSERT INTO test_table (test_string_column) VALUES (?), with [[uoyiydubxabzlyvei], [tcxexualtcrabhwkd], [unbruyxjtvyvhxtky], [demrmoesailfieezr], [jnllxfiansfbpkmmr]]

        java.sql.SQLException: operation not allowed: DML Returning cannot be batched

        java.sql.SQLException: operation not allowed: DML Returning cannot be batched
            at oracle.jdbc.driver.OraclePreparedStatement.addBatch(OraclePreparedStatement.java:10034)
            at oracle.jdbc.driver.OraclePreparedStatementWrapper.addBatch(OraclePreparedStatementWrapper.java:1001)
            at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.addBatch(HikariProxyPreparedStatement.java)
            at io.vertx.jdbcclient.impl.actions.JDBCPreparedBatch.execute(JDBCPreparedBatch.java:63)
            at io.vertx.jdbcclient.impl.actions.JDBCPreparedBatch.execute(JDBCPreparedBatch.java:45)
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
    fun `Broken - Batching multiple rows`() =
        runBlockingWithTimeoutUnit {
            val connection = SharedDbClient.pool.connection.await()
            val sql = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (?)"

            connection
                .batchQuery(sql, (1..5).map { arrayOf(randomString(16)) })
                .shouldBe(5)
        }

    /**
     * The workaround is to apply multiple inserts in a single transaction...
     */
    @Test
    fun `Solution - apply multiple inserts in a single transaction`() =
        runBlockingWithTimeoutUnit {
            val connection = SharedDbClient.pool.connection.await()
            val sql = "INSERT INTO $TEST_TABLE ($TEST_STRING_COLUMN_KEY) VALUES (?)"

            connection
                .multiInsertQuery(sql, (1..5).map { arrayOf(randomString(16)) })
                .shouldBe(5)
        }
}