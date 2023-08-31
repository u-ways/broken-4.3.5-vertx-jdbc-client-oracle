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
    implementation(rootProject.libs.kotlin.stdlib)

    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.kotest.assertions.core)
    testImplementation(rootProject.libs.kotest.runner.junit5)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
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
