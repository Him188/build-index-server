package me.him188.buildindex.routing

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import me.him188.buildindex.SimpleUserAuthenticator
import me.him188.buildindex.configureSecurity
import me.him188.buildindex.dto.*
import me.him188.buildindex.storage.*
import me.him188.buildindex.storage.Queries.createBranch
import me.him188.buildindex.storage.Queries.createModule
import me.him188.buildindex.storage.Queries.createUser
import me.him188.buildindex.storage.Queries.getBranches
import me.him188.buildindex.storage.Queries.grantPermission
import me.him188.buildindex.storage.Queries.grantPermissions
import org.jetbrains.exposed.sql.select
import java.util.*
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class IndexesKtTest : AbstractRoutingTest() {
    lateinit var testUserId: UUID

    context(ApplicationTestBuilder) override suspend fun configureApplication() {
        super.configureApplication()
        runTransaction {
            testUserId = createUser(clientCredentials.username, clientCredentials.password)!!
        }
        application {
            configureExceptionHandling()
            configureSecurity(SimpleUserAuthenticator(db))
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            configureRouting(db)
        }
    }

    @Test
    fun testRoot() = testApplication {
        client.get("/").run {
            assertStatus(OK)
            assertContains(bodyAsText(), "This is Index Server.")
        }
    }

    private suspend fun HttpResponse.assertStatus(status: HttpStatusCode) {
        assertEquals(status, this.status, this.bodyAsText().ifEmpty { this.status.toString() })
    }

    @Test
    fun testGetWhoami() = testApplication {
        runTransaction {
            val module: UUID = createModule("test")!!
            createBranch(module, "dev")!!
        }
        client.get("/v1/whoami").apply {
            assertStatus(OK)
            body<User>().run {
                assertEquals(clientCredentials.username, username)
                assertEquals(testUserId, id)
            }
        }
    }

    @Test
    fun testGetModules() = testApplication {
        runTransaction {
            val module: UUID = createModule("test")!!
            createBranch(module, "dev")
            grantPermission(testUserId, composeSinglePermission { root.moduleList() })!!
        }
        client.get("/v1/modules").run {
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
            testModuleId = createModule(testModuleName)!!
            testBranchId = createBranch(testModuleId, testBranchName)!!
            grantPermission(testUserId, composeSinglePermission { module(testModuleName).branchList() })!!
        }
        client.get("/v1/$testModuleName/branches") {
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
            testModuleId = createModule(testModuleName)!!
            grantPermission(testUserId, composeSinglePermission { module(testModuleName).branchCreate() })!!
        }
        client.put("/v1/$testModuleName/$testBranchName") {
        }.apply {
            assertStatus(Created)
            val body = body<Branch>()
            assertEquals(testBranchName, body.name)
            runTransaction {
                getBranches(testModuleName).single().toBranch().run {
                    assertEquals(testBranchName, name)
                    assertEquals(testModuleId, moduleId)
                    assertEquals(null, latestIndexId)
                }
            }
        }
    }

    @Test
    fun testGetIndexesLatestNoContent() = testApplication {
        val testModuleId: UUID
        val testBranchName = "dev"
        val testModuleName = "test"
        runTransaction {
            testModuleId = createModule(testModuleName)!!
            createBranch(testModuleId, testBranchName)!!
            grantPermission(
                testUserId,
                composeSinglePermission { module(testModuleName).branch(testBranchName).indexLatest() }
            )!!
        }
        client.get("/v1/$testModuleName/$testBranchName/indexes/latest").apply {
            assertStatus(NoContent)
        }
    }

    @Test
    fun testGetIndexesNext() = testApplication {
        val testModuleId: UUID
        val testBranchId: UUID
        val testBranchName = "dev"
        val testModuleName = "test"
        val testCommitRef = getRandomString(40)
        runTransaction {
            testModuleId = createModule(testModuleName)!!
            testBranchId = createBranch(testModuleId, testBranchName)!!
            grantPermission(
                testUserId,
                composeSinglePermission { module(testModuleName).branch(testBranchName).indexNext() }
            )!!
        }

        client.post("/v1/$testModuleName/$testBranchName/indexes/next") {
            parameter("commitRef", testCommitRef)
        }.apply {
            assertStatus(OK)
            val resp = body<NextIndexResp>()
            resp.run {
                assertEquals(testModuleId, resp.moduleId)
                assertEquals(testBranchId, resp.branchId)
                assertEquals(null, resp.previousIndexId)
                assertEquals(null, resp.previousIndexValue)

                // Check new index
                val index = newIndex
                assertEquals(testBranchId, index.branchId)
                assertEquals(testCommitRef, index.commitRef)
                assertEquals(1u, index.value)
                val timezone = TimeZone.currentSystemDefault()
                assertTrue {
                    index.date.toInstant(timezone) - LocalDateTime.now().toInstant(timezone) <= 1.minutes
                }
            }
        }
    }

    @Test
    fun testPostIndexesNextOK() = testApplication {
        val testModuleId: UUID
        val testBranchId: UUID
        val testBranchName = "dev"
        val testModuleName = "test"
        val testCommitRef = getRandomString(40)
        runTransaction {
            testModuleId = createModule(testModuleName)!!
            testBranchId = createBranch(testModuleId, testBranchName)!!
            grantPermissions(
                testUserId,
                composePermissions { module(testModuleName).branch(testBranchName).indexNext().indexLatest() }
            ).run { assertEquals(2, size) }
        }

        val testIndexId: UUID
        val testCommitDate: LocalDateTime
        client.post("/v1/$testModuleName/$testBranchName/indexes/next") {
            parameter("commitRef", testCommitRef)
        }.apply {
            assertStatus(OK)
            val resp = body<NextIndexResp>()
            resp.run {
                assertEquals(testModuleId, resp.moduleId)
                assertEquals(testBranchId, resp.branchId)
                assertEquals(null, resp.previousIndexId)
                assertEquals(null, resp.previousIndexValue)

                // Check new index
                val index = newIndex
                testIndexId = index.id
                testCommitDate = index.date
                assertEquals(testBranchId, index.branchId)
                assertEquals(testCommitRef, index.commitRef)
                assertEquals(1u, index.value)
                val timezone = TimeZone.currentSystemDefault()
                assertTrue {
                    index.date.toInstant(timezone) - LocalDateTime.now().toInstant(timezone) <= 1.minutes
                }
            }
        }

        client.get("/v1/$testModuleName/$testBranchName/indexes/latest").apply {
            assertStatus(OK)
            val body = body<Index>()
            assertEquals(testIndexId, body.id)
            assertEquals(testBranchId, body.branchId)
            assertEquals(testCommitRef, body.commitRef)
            assertEquals(1u, body.value)
            assertEquals(testCommitDate, body.date)
            runTransaction {
                Indexes.select { Indexes.id eq body.id }.single().toIndex().run {
                    assertEquals(id, testIndexId)
                    assertEquals(branchId, testBranchId)
                    assertEquals(commitRef, testCommitRef)
                    assertEquals(value, 1u)
                    assertEquals(date, testCommitDate)
                }
            }
        }
    }
}


fun getRandomString(length: Int): String =
    getRandomString(length, *defaultRanges)

private val defaultRanges: Array<CharRange> = arrayOf('a'..'z', 'A'..'Z', '0'..'9')

fun getRandomString(length: Int, vararg charRanges: CharRange): String =
    CharArray(length) { charRanges[Random.Default.nextInt(0..charRanges.lastIndex)].random() }.concatToString()
