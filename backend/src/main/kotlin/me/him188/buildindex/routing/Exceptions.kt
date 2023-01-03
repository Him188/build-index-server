package me.him188.buildindex.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        exception<NoMatchingBranchException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "")
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "")
        }
        exception<PermissionDeniedException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, cause.message ?: "")
        }
    }
}