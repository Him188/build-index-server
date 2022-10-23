package me.him188.indexserver.routing

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import me.him188.indexserver.SimpleUserAuthenticator
import me.him188.indexserver.configureSecurity
import me.him188.indexserver.dto.Branch
import me.him188.indexserver.dto.Module
import me.him188.indexserver.storage.Queries.createApplication
import me.him188.indexserver.storage.Queries.createBranch
import me.him188.indexserver.storage.Queries.createUser
import me.him188.indexserver.storage.Queries.getBranches
import me.him188.indexserver.storage.toBranch
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class IndexesKtTest : AbstractRoutingTest() {
    override suspend fun ApplicationTestBuilder.configureApplication() {
        runTransaction {
            createUser(clientCredentials.username, clientCredentials.password)
        }
        application {
            configureSecurity(SimpleUserAuthenticator(db))
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            configureIndexesRouting(db)
        }
    }

    @Test
    fun testRoot() = testApplication {
        client.get("/").run {
            assertStatus(OK)
            assertContains(bodyAsText(), "This is Index Server.")
        }
    }

    private fun HttpResponse.assertStatus(status: HttpStatusCode) {
        assertEquals(status, this.status)
    }

    @Test
    fun testGetWhoami() = testApplication {
        runTransaction {
            val module: UUID = createApplication("test")!!
            createBranch(module, "dev")
        }
        client.get("/whoami").apply {
            assertStatus(OK)
            assertEquals(clientCredentials.username, bodyAsText())
        }
    }

    @Test
    fun testGetApplications() = testApplication {
        runTransaction {
            val module: UUID = createApplication("test")!!
            createBranch(module, "dev")
        }
        client.get("/modules").run {
            assertStatus(OK)
            val result = body<List<Module>>()
            assertEquals(listOf("test"), result.map { it.name })
        }
    }

    @Test
    fun testGetBranches() = testApplication {
        val testModuleId: UUID
        val testBranchId: UUID
        val testBranchName = "dev"
        val testModuleName = "test"
        runTransaction {
            testModuleId = createApplication(testModuleName)!!
            testBranchId = createBranch(testModuleId, testBranchName)!!
        }
        client.get("/$testModuleName/branches") {
        }.apply {
            assertStatus(OK)
            val result = body<List<Branch>>()
            result.single().run {
                assertEquals(testModuleId, moduleId)
                assertEquals(testBranchId, id)
                assertEquals(testBranchName, testBranchName)
            }
        }
    }

    @Test
    fun testPutBranch() = testApplication {
        val testModuleId: UUID
        val testBranchName = "dev"
        val testModuleName = "test"
        runTransaction {
            testModuleId = createApplication(testModuleName)!!
        }
        client.put("/$testModuleName/$testBranchName") {
        }.apply {
            assertStatus(Created)
            val body = body<Branch>()
            assertEquals(testBranchName, body.name)
            runTransaction {
                getBranches(testModuleName).single().toBranch().run {
                    assertEquals(testBranchName, name)
                    assertEquals(testModuleId, moduleId)
                    assertEquals(null, latestIndex)
                }
            }
        }
    }
}