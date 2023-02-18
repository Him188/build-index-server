package me.him188.buildindex

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import me.him188.buildindex.cli.Command
import me.him188.buildindex.cli.CommandManager
import me.him188.buildindex.cli.CommandManagerImpl
import me.him188.buildindex.routing.configureExceptionHandling
import me.him188.buildindex.routing.configureRouting
import me.him188.buildindex.storage.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object IndexServerApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Welcome Build Index Server")

        val parser = ArgParser("IndexServer", useDefaultHelpShortName = false)

        val host by parser.option(ArgType.String, "host", "h", "Listen hostname").default("0.0.0.0")
        val port by parser.option(ArgType.Int, "port", "p", "Port number").default(5932)

        val workingDir by parser.option(ArgType.String, "dir", "wd", "Working directory")
            .default(System.getProperty("user.dir"))
        parser.parse(args)

//        val dbUrl by parser.option(ArgType.String, "db", description = "Database connection url").required()
//        val dbDriver by parser.option(ArgType.String, "dbdriver", description = "Database driver").required()
//        val dbUser by parser.option(ArgType.String, "dbuser", description = "User for database").default("")
//        val dbPassword by parser.option(ArgType.String, "dbpass", description = "Password for database").default("")

        println("Starting Build Index Server")
        File(workingDir).mkdirs()
        start(workingDir, port, host)
    }

    private fun start(workingDir: String, port: Int, host: String) {
        val appScope = CoroutineScope(CoroutineName("IndexServerApplication") + SupervisorJob())

        // h2jdbc:h2:mem:test
        val db =
            Database.connect("jdbc:h2:$workingDir/db;MODE=MYSQL", driver = "org.h2.Driver", user = "", password = "")
        initializeDatabase(db)

        val server = embeddedServer(Netty, port = port, host = host, module = { indexServerApplicationModule(db) })

        val commandManager = CommandManagerImpl()

        registerCommands(db, commandManager)


        val terminal: Terminal = TerminalBuilder.builder().jansi(true).build()
        val lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            //            .completer(MyCompleter())
            //            .highlighter(MyHighlighter())
            //            .parser(MyParser())
            .build()

        val commandExecutor: Channel<String> = Channel()

        thread {
            while (true) {
                val line = try {
                    lineReader.readLine()
                } catch (e: UserInterruptException) {
                    exitProcess(0)
                }
                commandExecutor.trySendBlocking(line)
                    .exceptionOrNull()?.printStackTrace()
            }
        }

        appScope.launch {
            while (isActive) {
                commandExecutor.receiveAsFlow().collect { line ->
                    try {
                        commandManager.executeCommandLine(line)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        }

//        println(composeSinglePermission { 
//            module("mirai-core").branch("dev").index()
//        })

//        transaction(db) {
////            Users.deleteWhere { username eq "mirai-github-actions" }
//            Users.insert {
//                it[username] = "mirai-github-actions"
//                it[password] = Random.nextBytes(16).also {
//                    println(it.toUHexString())
//                }
//            }
//        }

        server.start(true)
        appScope.cancel()
    }

    private fun registerCommands(db: Database, commandManager: CommandManager) {
        commandManager.register(Command("add-user", "Add a user") { (username, password) ->
            val existing = runTransaction(db) { Users.select { Users.username eq username }.singleOrNull()?.toUser() }
            if (existing != null) {
                throw IllegalArgumentException("User '$username' already exist.")
            }

            val count = runTransaction(db) {
                Users.insert {
                    it[Users.username] = username
                    it[Users.password] = UserPasswordEncryption.encrypt(password).value
                }.insertedCount
            }

            if (count == 1) {
                echo("Successfully added user '$username'.")
            } else {
                echo("Operation failed.")
            }
        })

        commandManager.register(Command("set-password", "Set a user's password") { (username, password) ->
            runTransaction(db) { Users.select { Users.username eq username }.singleOrNull()?.toUser() }
                ?: throw IllegalArgumentException("User '$username' not found.")

            val count = runTransaction(db) {
                Users.update({ Users.username eq username }) {
                    it[Users.password] = UserPasswordEncryption.encrypt(password).value
                }
            }

            if (count == 1) {
                echo("Successfully set.")
            } else {
                echo("Operation failed.")
            }
        })

        commandManager.register(Command("remove-user", "Add a user") { (username) ->
            val count = runTransaction(db) {
                Users.deleteWhere { Users.username eq username }
            }

            if (count == 1) {
                echo("Successfully deleted user '$username'.")
            } else {
                echo("Operation failed.")
            }
        })

        commandManager.register(Command("list-users", "List users") { _ ->
            val list = runTransaction(db) { Users.selectAll().map { it.toUser() } }

            echo("Total ${list.size} users: ")
            list.forEachIndexed { index, user ->
                echo("${index + 1}. ${user.username}")
            }
        })

        data class UserPermission(
            val username: String,
            val permission: String,
        )

        commandManager.register(Command("list-permissions", "List permissions") { _ ->
            val list = runTransaction(db) {
                UserPermissions.crossJoin(Users).selectAll()
                    .map { UserPermission(it[Users.username], it[UserPermissions.permission]) }
            }

            echo("Total ${list.size} permissions: ")
            list.forEachIndexed { index, perm ->
                echo("${index + 1}. ${perm.username}: ${perm.permission}")
            }
        })

        commandManager.register(Command("grant", "Grant permission") { (username, perm) ->
            val user = runTransaction(db) { Users.select { Users.username eq username }.singleOrNull()?.toUser() }
                ?: throw IllegalArgumentException("User '$username' not found.")

            val count = runTransaction(db) {
                UserPermissions.insert {
                    it[userId] = user.id
                    it[permission] = perm
                }.insertedCount
            }

            if (count == 1) {
                echo("Successfully granted '$username' with '$perm'.")
            } else {
                echo("Failed granting permission. Possibly '$username' is already granted with '$perm'.")
            }
        })

        commandManager.register(Command("revoke", "Revoke permission") { (username, perm) ->
            val user = runTransaction(db) { Users.select { Users.username eq username }.singleOrNull()?.toUser() }
                ?: throw IllegalArgumentException("User '$username' not found.")

            val count = runTransaction(db) {
                UserPermissions.deleteWhere(1) { (userId eq user.id) and (permission eq perm) }
            }

            if (count == 1) {
                echo("Successfully revoked '$perm' with '$username'.")
            } else {
                echo("Failed revoking permission. Possibly '$username' is not yet granted with '$perm'.")
            }
        })
    }

    fun initializeDatabase(db: Database) {
        transaction(db) {
            createMissingTablesAndColumns(Modules, Branches, Users, UserPermissions)
        }
    }
}

fun Application.indexServerApplicationModule(db: Database) {
    configureExceptionHandling()
    configureSecurity(SimpleUserAuthenticator(db))
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    configureRouting(db)
}


private fun ByteArray.toUHexString(
    separator: String = " ",
    offset: Int = 0,
    length: Int = this.size - offset
): String {
    if (length == 0) {
        return ""
    }
    val lastIndex = offset + length
    return buildString(length * 2) {
        this@toUHexString.forEachIndexed { index, it ->
            if (index in offset until lastIndex) {
                val ret = it.toUByte().toString(16).uppercase()
                if (ret.length == 1) append('0')
                append(ret)
                if (index < lastIndex - 1) append(separator)
            }
        }
    }
}
