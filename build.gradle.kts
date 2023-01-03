val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "1.7.20"
    id("io.ktor.plugin") version "2.1.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.20"
}

group = "me.him188.indexserver"
version = "0.0.1"
application {
    mainClass.set("me.him188.indexserver.IndexServerApplication")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.withType(Test::class.java) {
    useJUnitPlatform {
        this.includeEngines()
    }
}
repositories {
    mavenCentral()
}

kotlin.sourceSets.all {
    languageSettings.enableLanguageFeature("ContextReceivers")
    languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
    languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:$ktor_version"))
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-html-builder")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-host-common-jvm:2.1.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.1.2")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json")
    testImplementation("io.ktor:ktor-client-auth")

    implementation("com.h2database:h2:2.1.214")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.4.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-hocon")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    implementation(platform("org.jetbrains.exposed:exposed-bom:0.40.1"))
    implementation("org.jetbrains.exposed", "exposed-core")
    implementation("org.jetbrains.exposed", "exposed-dao")
    implementation("org.jetbrains.exposed", "exposed-jdbc")
    implementation("org.jetbrains.exposed", "exposed-kotlin-datetime")

    val jline = "3.21.0"
    implementation("org.jline:jline-terminal:$jline")
    implementation("org.jline:jline-terminal-jansi:$jline")
    implementation("org.jline:jline-reader:$jline")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm")
}