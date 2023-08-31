package io.github.u.ways.util

import io.github.u.ways.config.DATABASE_PASSWORD_ENV_KEY
import io.github.u.ways.config.DATABASE_URL_ENV_KEY
import io.github.u.ways.config.DATABASE_USERNAME_ENV_KEY

data class DatabaseClientConfiguration(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeoutInMs: Long,
    val connectionInitSql: String
)

object TestDatabaseConnectionDetails {
    fun url() = System.getenv(DATABASE_URL_ENV_KEY) ?: "jdbc:oracle:thin:@//localhost:1521/ORCLCDB1"
    fun user() = System.getenv(DATABASE_USERNAME_ENV_KEY) ?: "system"
    fun password() = System.getenv(DATABASE_PASSWORD_ENV_KEY) ?: "Oracl3!!"
}

fun testDatabaseClientConfiguration(
    url: String = TestDatabaseConnectionDetails.url(),
    user: String = TestDatabaseConnectionDetails.user(),
    password: String = TestDatabaseConnectionDetails.password(),
    maxPoolSize: Int = 5,
    connectionTimeoutInMs: Long = 30000L,
    connectionInitSql: String = "SELECT 1 FROM V\$DATABASE WHERE OPEN_MODE = 'READ WRITE'"
) = DatabaseClientConfiguration(
    url,
    user,
    password,
    maxPoolSize,
    connectionTimeoutInMs,
    connectionInitSql
)
