package me.him188.buildindex

import io.ktor.server.application.*
import io.ktor.server.auth.*
import me.him188.buildindex.storage.Users
import me.him188.buildindex.storage.runTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.security.MessageDigest
import java.util.*

const val LOGIN_AUTHENTICATION = "token"

data class UserUuidPrincipal(val uuid: UUID, val username: String) : Principal

val AuthenticationContext.uuidPrincipal: UserUuidPrincipal
    get() = principal as UserUuidPrincipal

val AuthenticationContext.userId: UUID
    get() = uuidPrincipal.uuid

fun Application.configureSecurity(userAuthenticator: UserAuthenticator) {
    authentication {
        basic(name = LOGIN_AUTHENTICATION) {
            realm = "Index Server"
            validate { credentials ->
                userAuthenticator.authenticate(
                    credentials.name,
                    UserPasswordEncryption.encrypt(credentials.password)
                )?.let {
                    UserUuidPrincipal(it, credentials.name)
                }
            }
        }
    }
}

interface UserAuthenticator {
    context(ApplicationCall) suspend fun authenticate(name: String, password: UserPassword): UUID?
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
    context(ApplicationCall) override suspend fun authenticate(name: String, password: UserPassword): UUID? {
        return runTransaction(db) {
            Users.select { (Users.username eq name) and (Users.password eq password.value) }.firstOrNull()
                ?.get(Users.id)?.value
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