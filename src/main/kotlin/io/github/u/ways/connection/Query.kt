package io.github.u.ways.connection

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Tuple

object Query {
    /**
     * Executes a BATCH query on the database.
     *
     * @param query The SQL query string to be executed.
     * @param arguments The batch of arguments to replace the placeholders in the prepared statement.
     * @return The number of affected rows.
     */
    suspend fun SqlConnection.batchQuery(
        query: String,
        arguments: List<Array<Any>> = emptyList(),
    ): Int =
        this.preparedQuery(query)
            .executeBatch(arguments.map(Tuple::from))
            .onFailure { e -> System.err.println("Failed to execute batch query: $query, with ${arguments.map(Array<Any>::toList)}\n\n$e") }
            .await()
            .rowCount()

    /**
     * Executes a multi-insert query on the database.
     *
     * @param query The SQL query string to be executed.
     * @param arguments The batch of arguments to replace the placeholders in the prepared statement.
     * @return The number of affected rows.
     */
    suspend fun SqlConnection.multiInsertQuery(
        query: String,
        arguments: List<Array<Any>> = emptyList(),
    ): Int = arguments.sumOf { updateQuery(query, *it) }

    /**
     * Executes an UPDATE query on the database.
     *
     * @param query The SQL query string to be executed.
     * @param arguments The arguments to replace the placeholders in the prepared statement.
     * @return The number of affected rows.
     */
    private suspend fun SqlConnection.updateQuery(
        query: String,
        vararg arguments: Any = emptyArray(),
    ): Int =
        this.preparedQuery(query)
            .execute(Tuple.from(arguments))
            .await()
            .rowCount()
}