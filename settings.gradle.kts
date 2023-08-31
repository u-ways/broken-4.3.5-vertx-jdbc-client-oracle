dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.8.10"
            val mockkVersion = "1.13.2"
            val kotestVersion = "5.5.4"
            val junitVersion = "5.8.2"

            library("kotlin-stdlib", "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            library("mockk", "io.mockk:mockk:$mockkVersion")
            library("kotest-runner-junit5", "io.kotest:kotest-runner-junit5:$kotestVersion")
            library("kotest-assertions-core", "io.kotest:kotest-assertions-core:$kotestVersion")
            library("junit-jupiter", "org.junit.jupiter:junit-jupiter:$junitVersion")
            plugin("jetbrains-kotlin-jvm", "org.jetbrains.kotlin.jvm").version(kotlinVersion)
        }
    }
}
