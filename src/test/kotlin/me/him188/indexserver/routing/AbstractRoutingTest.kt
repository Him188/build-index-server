package me.him188.indexserver.routing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import me.him188.indexserver.IndexServerApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

abstract class AbstractRoutingTest {
    @TempDir
    protected lateinit var tempDir: File

    val db by lazy {
        Database.connect("jdbc:h2:$tempDir/test;MODE=MYSQL").also {
            IndexServerApplication.initializeDatabase(it)
        }
    }

    val clientCredentials = BasicAuthCredentials("admin", "admin123")

    fun testApplication(
        configuration: suspend ApplicationTestBuilder.() -> Unit = { configureApplication() },
        testAction: suspend context(ClientProvider) () -> Unit
    ) = io.ktor.server.testing.testApplication test@{

        class MyClientProvider : ClientProvider {
            override val client: HttpClient by lazy {
                createClient { configureHttpClient() }
            }

            override fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
                return this@test.createClient(block)
            }
        }

        val provider = MyClientProvider()
        configuration()
        testAction(provider)
    }

    context(ApplicationTestBuilder) protected open suspend fun configureApplication() {

    }

    protected open fun HttpClientConfig<out HttpClientEngineConfig>.configureHttpClient() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(Auth) {
            basic {
                credentials {
                    clientCredentials
                }
            }
        }
    }
}

suspend fun <T> AbstractRoutingTest.runTransaction(statement: Transaction.() -> T): T {
    contract { callsInPlace(statement, InvocationKind.EXACTLY_ONCE) }
    return me.him188.indexserver.storage.runTransaction(db, statement)
}
