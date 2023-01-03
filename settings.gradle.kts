@file:Suppress("UnstableApiUsage")

rootProject.name = "build-index-server"


fun VersionCatalogBuilder.library(notation: String): VersionCatalogBuilder.LibraryAliasBuilder {
    val split = notation.split(":")
    return library(split[1], split[0], split[1])
}

dependencyResolutionManagement.versionCatalogs.create("libs") {
    val ktor = version("ktor", "2.2.1")
    val kotlin = version("kotlin", "1.8.0")
    val kotlinxSerialization = version("kotlinx-serialization", "1.4.1")
    val kotlinxDatetime = version("kotlinx-datetime", "0.4.0")
    val exposed = version("exposed", "0.40.1")
    val jline = version("jline", "3.21.0")
    val junit = version("junit", "5.9.1")

    library("ktor-server-core", "io.ktor", "ktor-server-core").versionRef(ktor)
    library("ktor-server-auth", "io.ktor", "ktor-server-auth").versionRef(ktor)
    library("ktor-server-content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef(ktor)
    library("ktor-server-html-builder", "io.ktor", "ktor-server-html-builder").versionRef(ktor)
    library("ktor-server-netty", "io.ktor", "ktor-server-netty").versionRef(ktor)
    library("ktor-server-status-pages", "io.ktor", "ktor-server-status-pages").versionRef(ktor)
    library("ktor-server-host-common", "io.ktor", "ktor-server-host-common").versionRef(ktor)

    library("ktor-client-content-negotiation", "io.ktor", "ktor-client-content-negotiation").versionRef(ktor)
    library("ktor-serialization-kotlinx-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef(ktor)
    library("ktor-client-auth", "io.ktor", "ktor-client-auth").versionRef(ktor)
    library("io.ktor:ktor-server-tests").versionRef(ktor)

    library("com.h2database:h2").version("2.1.214")
    library("ch.qos.logback:logback-classic").version("1.4.4")
    library("org.jetbrains.kotlinx:kotlinx-cli").version("0.3.5")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core").version("1.6.4")

    library("org.jetbrains.kotlinx:kotlinx-serialization-hocon").versionRef(kotlinxSerialization)
    library("org.jetbrains.kotlinx:kotlinx-serialization-json").versionRef(kotlinxSerialization)

    library("org.jetbrains.kotlinx:kotlinx-datetime").versionRef(kotlinxDatetime)

    library("exposed-core", "org.jetbrains.exposed", "exposed-core").versionRef(exposed)
    library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").versionRef(exposed)
    library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef(exposed)
    library("exposed-kotlin-datetime", "org.jetbrains.exposed", "exposed-kotlin-datetime").versionRef(exposed)

    library("org.jline:jline-terminal").versionRef(jline)
    library("org.jline:jline-terminal-jansi").versionRef(jline)
    library("org.jline:jline-reader").versionRef(jline)

    library("org.junit.jupiter:junit-jupiter-engine").versionRef(junit)
    library("org.junit.vintage:junit-vintage-engine").versionRef(junit)
    library("org.jetbrains.kotlin:kotlin-test-junit5").versionRef(kotlin)


    plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").versionRef(kotlin)
    plugin("kotlin-serialization", "org.jetbrains.kotlin.plugin.serialization").versionRef(kotlin)
    plugin("ktor", "io.ktor.plugin").versionRef(ktor)
    plugin("maven-central-publish", "me.him188.maven-central-publish").version("1.0.0")
}



include("backend")
include("api")
project(":api").name = "build-index-api"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")