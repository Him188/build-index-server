package me.him188.indexserver.routing

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.datetime.LocalDateTime
import me.him188.indexserver.dto.Branch
import me.him188.indexserver.dto.Index
import me.him188.indexserver.dto.Module
import me.him188.indexserver.storage.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.util.*

fun Application.configureIndexesRouting(db: Database): Routing = routing {
    route("/") {
        get {
            call.respond(StaticContents.Welcome)
        }

        authenticate("token") {
            /**
             * Gets authenticated username
             */
            get("whoami") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText(principal.name)
            }

            /**
             * Gets list of modules
             */
            get("modules") {
                val result: List<Module> = runTransaction(db) { Queries.getModules().map { it.toApplication() } }
                call.respondOK(result)
            }

            /**
             * Gets list of branches of a module
             */
            get("{module}/branches") {
                val module: String by call.parameters
                val result: List<Branch> = runTransaction(db) {
                    Queries.getBranches(module).map { it.toBranch() }
                }

                call.respondOK(result)
            }

            /**
             * Creates a new branch
             */
            put("{module}/{branch}") {
                val module: String by call.parameters
                val branch: String by call.parameters
                val moduleId: UUID
                val result: UUID? = runTransaction(db) {
                    moduleId =
                        Queries.getModule(module)?.get(Modules.id)?.value ?: throw NoSuchElementException("module")
                    Queries.createBranch(moduleId, branch)
                }

                if (result == null) {
                    call.respond(Conflict)
                } else {
                    call.respond(
                        Created, Branch(
                            id = result,
                            moduleId = moduleId,
                            name = branch,
                            latestIndex = null,
                        )
                    )
                }
            }

            route("{module}/{branch}/indexes/") {
                /**
                 * Filters indexes.
                 * @return list of [Index]
                 */
                get {
                    val module: String by call.parameters
                    val branch: String by call.parameters

                    val id: UUID? = call.parameters["id"]?.let { UUID.fromString(it) }
                    val commitRef: String? = call.parameters["commitRef"]
                    val index: UInt? = call.parameters["index"]?.toUInt()

                    val date: LocalDateTime? = call.parameters["date"]?.let { LocalDateTime.parse(it) }
                    val dateBefore: LocalDateTime? = call.parameters["dateAfter"]?.let { LocalDateTime.parse(it) }
                    val dateAfter: LocalDateTime? = call.parameters["dateBefore"]?.let { LocalDateTime.parse(it) }
//
//                    val datetime: LocalDateTime? = call.parameters["datetime"]?.let { LocalDateTime.parse(it) }
//                    val datetimeBefore: LocalDateTime? =`
//                        call.parameters["datetimeAfter"]?.let { LocalDateTime.parse(it) }
//                    val datetimeAfter: LocalDateTime? =
//                        call.parameters["datetimeBefore"]?.let { LocalDateTime.parse(it) }

                    val result: List<Index> = runTransaction(db) {
                        (Modules crossJoin Branches crossJoin Indexes).select {
                            (Modules.name eq module).and(Branches.name eq branch)
                                .andIfNotNull(id) { Indexes.id eq it }
                                .andIfNotNull(commitRef) { Indexes.commitRef eq it }
                                .andIfNotNull(index) { Indexes.value eq it }
                                .andIfNotNull(date) { Indexes.date eq it }
                                .andIfNotNull(dateBefore) { Indexes.date less it }
                                .andIfNotNull(dateAfter) { Indexes.date greater it }
//                                .andIfNotNull(datetime) { Indexes.date eq it }
//                                .andIfNotNull(datetimeBefore) { Indexes.date less it }
//                                .andIfNotNull(datetimeAfter) { Indexes.date greater it }
                        }.map { it.toIndex() }
                    }

                    call.respond(result)
                }

                /**
                 * Gets the latest index
                 */
                get("latest") {
                    val module: String by call.parameters
                    val branch: String by call.parameters

                    val index = runTransaction(db) {
                        val index = Queries.getLatestIndex(module, branch)
                        if (index != null) {
                            (Modules crossJoin Branches crossJoin Indexes).select {
                                (Modules.name eq module)
                                    .and(Branches.name eq branch)
                                    .and(Indexes.value eq Branches.latestIndexValue)
                            }.firstOrNull()?.toIndex()
                        } else {
                            null
                        }
                    }

                    call.respondOkOrNotFound(index)
                }

                route("{index}") {
                    /**
                     * Gets information about this index.
                     */
                    get {
                        val module: String by call.parameters
                        val branch: String by call.parameters
                        val index: UInt by call.parameters

                        val result = runTransaction(db) {
                            (Modules crossJoin Branches crossJoin Indexes).select {
                                (Modules.name eq module) and (Branches.name eq branch) and (Indexes.value eq index)
                            }.firstOrNull()?.toIndex()
                        }

                        call.respondOkOrNotFound(result)
                    }
                    put {
                        val module: String by call.parameters
                        val branch: String by call.parameters
                        val req: UInt by call.parameters

                        val result = runTransaction(db) {
                            Queries.setLatestIndex(module, branch, newIndex = req)
                        }
                        call.respondOkOrNotFound(result)
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondOkOrNotFound(result: Boolean) {
    if (result) {
        respond(HttpStatusCode.OK)
    } else {
        respond(HttpStatusCode.NotFound)
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.respondOkOrNotFound(result: T?) {
    if (result != null) {
        respond(HttpStatusCode.OK, result)
    } else {
        respond(HttpStatusCode.NotFound)
    }
}


private suspend inline fun <reified T : Any> ApplicationCall.respondOK(content: T) {
    return respond(HttpStatusCode.OK, content)
}
