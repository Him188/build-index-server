package me.him188.indexserver.storage

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Users : UUIDTable() {
    val username = varchar("username", 64).uniqueIndex()
    val password = binary("password", 16)
}

enum class OperationPermissions {
    READ_INDEX,
    WRITE_INDEX,
}

object UserPermissions : Table() {
    val userId = reference("userId", Users.id, onDelete = ReferenceOption.CASCADE)
    val permission = enumeration<OperationPermissions>("permission")

    init {
        uniqueIndex(userId, permission)
    }

    override val primaryKey: PrimaryKey = PrimaryKey(userId, permission)
}

