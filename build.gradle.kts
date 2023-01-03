@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    java
}

allprojects {
    group = "me.him188.buildindex"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }

    afterEvaluate {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
        runCatching {
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