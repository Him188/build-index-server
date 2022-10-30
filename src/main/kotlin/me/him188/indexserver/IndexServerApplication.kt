package me.him188.indexserver

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.serialization.json.Json
import me.him188.indexserver.routing.configureRouting
import me.him188.indexserver.storage.Branches
import me.him188.indexserver.storage.Modules
import me.him188.indexserver.storage.UserPermissions
import me.him188.indexserver.storage.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.transactions.transaction

object IndexServerApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("IndexServer", useDefaultHelpShortName = false)

        val host by parser.option(ArgType.String, "host", "h", "Listen hostname").default("0.0.0.0")
        val port by parser.option(ArgType.Int, "port", "p", "Port number").default(5939)

        val workingDir by parser.option(ArgType.String, "dir", "wd", "Working directory")
            .default(System.getProperty("user.dir"))

//        val dbUrl by parser.option(ArgType.String, "db", description = "Database connection url").required()
//        val dbDriver by parser.option(ArgType.String, "dbdriver", description = "Database driver").required()
//        val dbUser by parser.option(ArgType.String, "dbuser", description = "User for database").default("")
//        val dbPassword by parser.option(ArgType.String, "dbpass", description = "Password for database").default("")

        parser.parse(args)

        // h2jdbc:h2:mem:test
        val db =
            Database.connect("jdbc:h2:$workingDir/db;MODE=MYSQL", driver = "org.h2.Driver", user = "", password = "")
        initializeDatabase(db)

        embeddedServer(Netty, port = port, host = host) {
            configureSecurity(SimpleUserAuthenticator(db))
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            configureRouting(db)
        }.start(wait = true)
    }

    fun initializeDatabase(db: Database) {
        transaction(db) {
            createMissingTablesAndColumns(Modules, Branches, Users, UserPermissions)
        }
    }
}

