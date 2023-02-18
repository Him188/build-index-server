@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    java
}

allprojects {
    group = "me.him188.buildindex"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }

    afterEvaluate {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
        runCatching {
            kotlin.jvmToolchain(8)
            kotlin.sourceSets.all {
                languageSettings.enableLanguageFeature("ContextReceivers")
                languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
                languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            }
        }
    }

    tasks.withType(Test::class.java) {
        useJUnitPlatform {
            this.includeEngines()
        }
    }
}

application {
    applicationName = "build-index-server"
}