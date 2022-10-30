package me.him188.indexserver.routing

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.indexserver.LOGIN_AUTHENTICATION
import me.him188.indexserver.dto.*
import me.him188.indexserver.dto.Index
import me.him188.indexserver.storage.*
import me.him188.indexserver.userId
import me.him188.indexserver.uuidPrincipal
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import java.util.*

fun Application.configureRouting(db: Database): Routing = routing {
    /**
     * Server Root
     */
    route("/") {
        /**
         * Gets welcome info
         */
        get {
            call.respond(StaticContents.Welcome)
        }
    }

    /**
     * API version 1
     */
    route("/v1/") {
        routingVersion1(db)
    }
}

private fun Route.routingVersion1(db: Database) = with(DatabaseContext(db)) {
    /**
     * @throws Unauthorized if not authenticated
     */
    authenticate(LOGIN_AUTHENTICATION) {
        users()

        /**
         * Gets list of modules
         * @return [OK] with [List] of [Module]s
         */
        route("modules") {
            get {
                call.checkPermission(ModulePermission.MODULE_LIST)
                val result: List<Module> = runTransaction(db) { Queries.getModules().map { it.toApplication() } }
                call.respondOK(result)
            }
        }

        /**
         * Creates a new module
         */
        put("{module}") {
            call.checkPermission(ModulePermission.MODULE_CREATE)
            val module: String by call.parameters
            val result: UUID? = runTransaction(db) {
                Modules.insertIgnoreAndGetId {
                    it[name] = module
                }?.value
            }
            if (result == null) {
                call.respond(Conflict)
            } else {
                call.respondOK(result)
            }
        }

        /**
         * Gets list of branches of a module
         * @return [OK] with [List] of [Branch]es
         */
        get("{module}/branches") {
            call.checkPermission(BranchPermission.BRANCH_LIST)
            val module: String by call.parameters
            val result: List<Branch> = runTransaction(db) {
                Queries.getBranches(module).map { it.toBranch() }
            }

            call.respondOK(result)
        }

        /**
         * Creates a new branch
         * @return [Created] with [Branch]; or [Conflict] if module with given name already exists.
         */
        put("{module}/{branch}") {
            call.checkPermission(BranchPermission.BRANCH_CREATE)
            val module: String by call.parameters
            val branch: String by call.parameters
            val moduleId: UUID
            val result: UUID? = runTransaction(db) {
                moduleId = Queries.getModule(module)?.get(Modules.id)?.value
                    ?: throw NoSuchElementException("module")
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
                        latestIndexId = null,
                    )
                )
            }
        }

        route("{module}/{branch}/indexes/") {
            /**
             * Gets indexes matching filter
             * @return [OK] with [List] of [Index].
             */
            get {
                call.checkPermission(IndexPermission.INDEX_LIST)
                val result: List<Index> = runTransaction(db) {
                    queryIndexesInFilter(call.parameters).map { it.toIndex() }
                }

                call.respond(result)
            }

            /**
             * Gets indexes matching filter.
             * @return [OK] with [Int] — number of indexes deleted
             */
            delete {
                call.checkPermission(IndexPermission.INDEX_DELETE)
                val result: Int = runTransaction(db) {
                    Indexes.deleteIgnoreWhere {
                        id inSubQuery queryIndexesInFilter(call.parameters)
                    }
                }

                call.respond(result)
            }

            /**
             * Gets the latest index
             * @return [OK] with [Index]; [NoContent] if the branch is new; or [NotFound] if module + branch are invalid.
             */
            get("latest") {
                call.checkPermission(IndexPermission.INDEX_LATEST)
                val module: String by call.parameters
                val branch: String by call.parameters

                val index = runTransaction(db) {
                    val index = Queries.getLatestIndexId(module, branch)?.value
                    if (index != null) {
                        (Modules crossJoin Branches crossJoin Indexes).select {
                            (Modules.name eq module)
                                .and(Branches.name eq branch)
                                .and(Indexes.id eq index)
                        }.firstOrNull()?.toIndex()
                    } else {
                        null
                    }
                }

                call.respondOkOrNoContent(index)
            }

//            /**
//             * Creates an index with specified information.
//             * @return [OK] with [Index]; or [NotFound].
//             */
//            post("create") {
//                val module: String by call.parameters
//                val branch: String by call.parameters
//
//                val id: UUID = call.parameters["id"]?.let { UUID.fromString(it) } ?: UUID.randomUUID()
//                val branch: String by call.parameters
//                val branch: String by call.parameters
//                val branch: String by call.parameters
//
//                val index = runTransaction(db) {
//                    val index = Queries.getLatestIndexId(module, branch)
//                    if (index != null) {
//                        (Modules crossJoin Branches crossJoin Indexes).select {
//                            (Modules.name eq module)
//                                .and(Branches.name eq branch)
//                                .and(Indexes.value eq Branches.latestIndexValue)
//                        }.firstOrNull()?.toIndex()
//                    } else {
//                        null
//                    }
//                }
//
//                call.respondOkOrNotFound(index)
//            }

            /**
             * Creates an index based on the latest one
             * @return [OK] with [NextIndexResp]; or [NotFound] if `module` and `branch` provided are invalid.
             */
            post("next") {
                call.checkPermission(IndexPermission.INDEX_NEXT)

                val module: String by call.parameters
                val branchName: String = call.parameters.getOrFail("branch")
                val commitRef: String by call.parameters

                val branch: Branch
                val newIndexId: UUID
                val newIndexValue: UInt
                val newDate = LocalDateTime.now()
                val latestIndexValue: UInt?
                val updatedCount = runTransaction(db) {
                    // Get the branch
                    branch = Queries.getBranch(module, branchName)?.toBranch() ?: throw NoMatchingBranchException()

                    // Get latest index
                    latestIndexValue = if (branch.latestIndexId == null) {
                        null
                    } else {
                        Indexes.select {
                            (Indexes.id eq branch.latestIndexId) and (Indexes.branchId eq branch.id)
                        }.firstOrNull()?.get(Indexes.value)
                    }

                    // Insert new Index
                    newIndexValue = latestIndexValue?.inc() ?: 1u
                    newIndexId = Indexes.insertAndGetId {
                        it[branchId] = branch.id
                        it[Indexes.commitRef] = commitRef
                        it[value] = newIndexValue
                        it[date] = newDate
                    }.value

                    // Update `Branches.latestIndexId`
                    Branches.update(where = {
                        (Branches.moduleId eq branch.moduleId) and (Branches.id eq branch.id)
                    }) {
                        it[latestIndexId] = newIndexId
                    }
                }
                check(updatedCount == 1) { "No matching module and branch" }

                call.respondOK(
                    NextIndexResp(
                        moduleId = branch.moduleId,
                        branchId = branch.id,
                        previousIndexId = branch.latestIndexId,
                        previousIndexValue = latestIndexValue,
                        newIndex = Index(
                            id = newIndexId,
                            branchId = branch.id,
                            commitRef = commitRef,
                            value = newIndexValue,
                            date = newDate
                        )
                    )
                )
            }

            route("{index}") {
                /**
                 * Gets information about this index.
                 */
                get {
                    call.checkPermission(IndexPermission.INDEX_READ)
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
                /**
                 * Delete this index
                 */
                delete {
                    call.checkPermission(IndexPermission.INDEX_DELETE)
                    val module: String by call.parameters
                    val branch: String by call.parameters
                    val req: UInt by call.parameters

                    throw UnsupportedOperationException()

//                    val result = runTransaction(db) {
//                    }
//                    call.respondOkOrNotFound(result)
                }
            }
        }
    }
}

class NoMatchingBranchException : NoSuchElementException("Invalid module and/or branch")
class PermissionDeniedException(permission: String) :
    IllegalStateException("Permission denied. Required permission token: '$permission'")

fun LocalDateTime.Companion.now(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    Clock.System.now().toLocalDateTime(timeZone)

private fun Route.users() {
    /**
     * Gets authenticated username
     * @return [OK] with [User]
     */
    get("whoami") {
        val principal = call.authentication.uuidPrincipal
        call.respondOK(User(principal.uuid, principal.username))
    }
}


private fun queryIndexesInFilter(
    parameters: Parameters
): Query {
    val module: String by parameters
    val branch: String by parameters

    val id: UUID? = parameters["id"]?.let { UUID.fromString(it) }
    val commitRef: String? = parameters["commitRef"]
    val index: UInt? = parameters["index"]?.toUInt()

    val date: LocalDateTime? = parameters["date"]?.let { LocalDateTime.parse(it) }
    val dateBefore: LocalDateTime? = parameters["dateAfter"]?.let { LocalDateTime.parse(it) }
    val dateAfter: LocalDateTime? = parameters["dateBefore"]?.let { LocalDateTime.parse(it) }

//                    val datetime: LocalDateTime? = call.parameters["datetime"]?.let { LocalDateTime.parse(it) }
//                    val datetimeBefore: LocalDateTime? =`
//                        call.parameters["datetimeAfter"]?.let { LocalDateTime.parse(it) }
//                    val datetimeAfter: LocalDateTime? =
//                        call.parameters["datetimeBefore"]?.let { LocalDateTime.parse(it) }

    return queryIndexesInFilter(module, branch, id, commitRef, index, date, dateBefore, dateAfter)
}

private fun queryIndexesInFilter(
    module: String,
    branch: String,
    id: UUID?,
    commitRef: String?,
    index: UInt?,
    date: LocalDateTime?,
    dateBefore: LocalDateTime?,
    dateAfter: LocalDateTime?
) = (Modules crossJoin Branches crossJoin Indexes).select {
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
}

private suspend fun ApplicationCall.respondOkOrNotFound(result: Boolean) {
    if (result) {
        respond(OK)
    } else {
        respond(NotFound)
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.respondOkOrNotFound(result: T?) {
    if (result != null) {
        respond(OK, result)
    } else {
        respond(NotFound)
    }
}


private suspend inline fun <reified T : Any> ApplicationCall.respondOkOrNoContent(result: T?) {
    if (result != null) {
        respond(OK, result)
    } else {
        respond(NoContent)
    }
}


private suspend inline fun <reified T : Any> ApplicationCall.respondOK(content: T) {
    return respond(OK, content)
}


class DatabaseContext(
    val db: Database
)

/**
 * @throws PermissionDeniedException
 */
context (DatabaseContext) suspend fun ApplicationCall.checkPermission(permission: PermissionToken) {
    val module: String by parameters
    val branch: String by parameters

    val id = authentication.userId
    val permissionStr = composeSinglePermission {
        when (permission) {
            is IndexPermission -> {
                module(module).branch(branch).token(permission)
            }
            is BranchPermission -> {
                module(module).token(permission)
            }
            is ModulePermission -> {
                root.token(permission)
            }
        }
    }
    runTransaction(db) {
        val result = testPermission(
            filterUser = { Users.id eq id },
            permission = permissionStr
        )

        if (!result) {
            throw PermissionDeniedException(permissionStr)
        }
    }
}