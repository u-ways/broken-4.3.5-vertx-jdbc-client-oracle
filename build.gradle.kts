import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `java-library`
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "io.github.u.ways"
version = System.getenv("VERSION") ?: "DEV-SNAPSHOT"
description = "Demo of JDBCColumnDescriptor inflexible type mapping."

repositories(RepositoryHandler::mavenCentral)

dependencies {
    val vertxVersion = "4.4.5"
    val kotlinAwaitilityVersion = "4.1.0"
    val hikariCPVersion = "5.0.1"
    val ojdbc11Version = "21.9.0.0"
    val jacksonVersion = "2.15.2"

    implementation("io.vertx:vertx-config:$vertxVersion")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-jdbc-client:$vertxVersion")
    implementation("io.vertx:vertx-sql-client:$vertxVersion")
    implementation("io.vertx:vertx-sql-client-templates:$vertxVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("com.oracle.database.jdbc:ojdbc11:$ojdbc11Version")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$kotlinAwaitilityVersion")

    implementation(rootProject.libs.kotlin.stdlib)
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.kotest.assertions.core)
    testImplementation(rootProject.libs.kotest.runner.junit5)
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(1 + VERSION_17.ordinal)
        )
    }
}

tasks {
    test {
        useJUnitPlatform()
        testLogging { showStandardStreams = true }
    }

    wrapper {
        distributionType = ALL
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = VERSION_17.toString()
    }
}
