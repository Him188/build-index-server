@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.maven.central.publish)
}

kotlin {
    sourceSets {
        main {
            explicitApi()
        }
    }
}

application {
    mainClass.set("me.him188.buildindex.IndexServerApplication")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlin.test.junit5)
}

mavenCentralPublish {
    useCentralS01()
    artifactId = "build-index-api"
    group = "me.him188.buildindex"

    singleDevGithubProject("Him188", "build-index-server")
}