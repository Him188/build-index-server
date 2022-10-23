package me.him188.indexserver.storage

import me.him188.indexserver.UserPasswordEncryption
import me.him188.indexserver.dto.Branch
import me.him188.indexserver.dto.Index
import me.him188.indexserver.dto.Module
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
    fun createApplication(name: String): UUID? =
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
    fun createUser(username: String, password: String): UUID? =
        Users.insertIgnoreAndGetId {
            it[Users.username] = username
            it[Users.password] = UserPasswordEncryption.encrypt(password).value
        }?.value

    context(Transaction)
    fun getLatestIndex(moduleName: String, branch: String): UInt? =
        (Modules crossJoin Branches).select {
            (Modules.name eq moduleName) and (Branches.name eq branch)
        }.firstOrNull()?.get(Branches.latestIndexValue)

    context(Transaction)
    fun getBranchIndexes(moduleName: String, branch: String): Query =
        (Modules crossJoin Branches crossJoin Indexes).select {
            (Modules.name eq moduleName) and (Branches.name eq branch)
        }

    context(Transaction)
    fun setLatestIndex(moduleName: String, branch: String, newIndex: UInt) =
        (Modules crossJoin Branches).update(
            where = { (Modules.name eq moduleName) and (Branches.name eq branch) },
            limit = 1
        ) {
            it[Branches.latestIndexValue] = newIndex
        } == 1
}

fun ResultRow.toBranch(): Branch {
    return Branch(
        id = get(Branches.id).value,
        moduleId = get(Modules.id).value,
        name = get(Branches.name),
        latestIndex = get(Branches.latestIndexValue),
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
            index = get(value),
            date = get(date),
        )
    }
}

inline fun <T : Any> Op<Boolean>.andIfNotNull(
    arg: T?,
    op: SqlExpressionBuilder.(T) -> Op<Boolean>?
): Op<Boolean> = if (arg == null) this else andIfNotNull(SqlExpressionBuilder.op(arg))
