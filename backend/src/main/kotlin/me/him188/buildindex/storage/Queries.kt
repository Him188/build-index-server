package me.him188.buildindex.storage

import me.him188.buildindex.UserPasswordEncryption
import me.him188.buildindex.dto.Branch
import me.him188.buildindex.dto.Index
import me.him188.buildindex.dto.Module
import me.him188.buildindex.dto.User
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.util.*

object Queries {
    context(Transaction)
    fun getModules() =
        Modules.selectAll()

    context(Transaction)
    fun getBranches(moduleName: String) =
        (Modules crossJoin Branches).select { Modules.name eq moduleName }

    context(Transaction)
    fun getModule(moduleName: String) =
        Modules.select { Modules.name eq moduleName }.limit(1).firstOrNull()

    context(Transaction)
    fun createModule(name: String): UUID? =
        Modules.insertIgnoreAndGetId {
            it[Modules.name] = name
        }?.value

    context(Transaction)
    fun createBranch(moduleId: UUID, name: String): UUID? =
        Branches.insertIgnoreAndGetId {
            it[Branches.moduleId] = moduleId
            it[Branches.name] = name
        }?.value

    context(Transaction)
    fun grantPermission(userId: UUID, permission: Permission): UUID? {
        return UserPermissions.insertIgnoreAndGetId {
            it[UserPermissions.userId] = userId
            it[UserPermissions.permission] = permission
        }?.value
    }

    context(Transaction)
    fun grantPermissions(userId: UUID, permissions: Set<Permission>): Set<UUID> {
        return UserPermissions.batchInsert(permissions, ignore = true, shouldReturnGeneratedValues = false) {
            set(UserPermissions.userId, userId)
            set(UserPermissions.permission, it)
        }.mapTo(mutableSetOf()) { it[UserPermissions.id].value }
    }

    context(Transaction)
    fun createUser(username: String, password: String): UUID? =
        Users.insertIgnoreAndGetId {
            it[Users.username] = username
            it[Users.password] = UserPasswordEncryption.encrypt(password).value
        }?.value

    context(Transaction)
    fun getLatestIndexId(moduleName: String, branch: String): EntityID<UUID>? =
        getBranch(moduleName, branch)?.get(Branches.latestIndexId)

    context(Transaction)
    fun getBranch(moduleName: String, branch: String): ResultRow? =
        (Modules crossJoin Branches).select {
            (Modules.name eq moduleName) and (Branches.name eq branch)
        }.firstOrNull()

    context(Transaction)
    fun getBranchIndexes(moduleName: String, branch: String): Query =
        (Modules crossJoin Branches crossJoin Indexes).select {
            (Modules.name eq moduleName) and (Branches.name eq branch)
        }

    context(Transaction)
    fun setLatestIndex(moduleName: String, branch: String, newIndex: UUID) =
        (Modules crossJoin Branches).update(
            where = { (Modules.name eq moduleName) and (Branches.name eq branch) },
            limit = 1
        ) {
            it[Branches.latestIndexId] = newIndex
        } == 1
}

fun ResultRow.toBranch(): Branch {
    return Branch(
        id = get(Branches.id).value,
        moduleId = get(Modules.id).value,
        moduleName = get(Modules.name),
        name = get(Branches.name),
        latestIndexId = get(Branches.latestIndexId)?.value,
    )
}

fun ResultRow.toUser(): User {
    return User(
        id = get(Users.id).value,
        username = get(Users.username),
    )
}

fun ResultRow.toApplication(): Module {
    return Module(
        id = get(Modules.id).value,
        name = get(Modules.name),
    )
}

fun ResultRow.toIndex(): Index {
    with(Indexes) {
        return Index(
            id = get(id).value,
            branchId = get(branchId).value,
            commitRef = get(commitRef),
            value = get(value),
            date = get(date),
        )
    }
}

inline fun <T : Any> Op<Boolean>.andIfNotNull(
    arg: T?,
    op: SqlExpressionBuilder.(T) -> Op<Boolean>?
): Op<Boolean> = if (arg == null) this else andIfNotNull(SqlExpressionBuilder.op(arg))
