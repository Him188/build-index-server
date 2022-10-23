package me.him188.indexserver

import io.ktor.server.application.*
import io.ktor.server.auth.*
import me.him188.indexserver.storage.Users
import me.him188.indexserver.storage.runTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.security.MessageDigest

const val LOGIN_AUTHENTICATION = "token"

fun Application.configureSecurity(userAuthenticator: UserAuthenticator) {
    authentication {
        basic(name = LOGIN_AUTHENTICATION) {
            realm = "Index Server"
            validate { credentials ->
                if (userAuthenticator.authenticate(
                        credentials.name,
                        UserPasswordEncryption.encrypt(credentials.password)
                    )
                ) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}

interface UserAuthenticator {
    context(ApplicationCall) suspend fun authenticate(name: String, password: UserPassword): Boolean
}

@JvmInline
value class UserPassword(
    val value: ByteArray
)

object UserPasswordEncryption {
    fun encrypt(password: String): UserPassword {
        return UserPassword(Digest.md5(password))
    }
}

internal class SimpleUserAuthenticator(
    private val db: Database,
) : UserAuthenticator {
    context(ApplicationCall) override suspend fun authenticate(name: String, password: UserPassword): Boolean {
        return runTransaction(db) {
            Users.select() { (Users.username eq name) and (Users.password eq password.value) }.notEmpty()
        }
    }
}

private object Digest {
    fun md5(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(input)!!
    }

    fun md5(input: String) = md5(input.toByteArray())
}

fun Query.notEmpty() = !empty()