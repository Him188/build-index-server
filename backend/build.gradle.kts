@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("me.him188.buildindex.IndexServerApplication")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.buildIndexApi)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)

    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.auth)

    implementation(libs.h2)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.coroutines.core)


    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.hocon)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)

    implementation(libs.jline.terminal)
    implementation(libs.jline.terminal.jansi)
    implementation(libs.jline.reader)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.tests)
}