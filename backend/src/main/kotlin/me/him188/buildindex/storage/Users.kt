package me.him188.buildindex.storage

import me.him188.buildindex.notEmpty
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*

object Users : UUIDTable() {
    val username = varchar("username", 64).uniqueIndex()
    val password = binary("password", 16)
}

object UserPermissions : UUIDTable() {
    val userId = reference("userId", Users.id, onDelete = ReferenceOption.CASCADE)
    val permission = varchar("permission", 150)

    init {
        uniqueIndex(userId, permission)
    }
}

context(Transaction) inline fun testPermission(
    filterUser: SqlExpressionBuilder.() -> Op<Boolean>,
    permission: String
): Boolean {
    return (Users crossJoin UserPermissions).select {
        filterUser() and (UserPermissions.permission eq permission)
    }.notEmpty()
}