package me.him188.buildindex.storage

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Modules : UUIDTable("Modules") {
    val name = varchar("name", 64).uniqueIndex()
}

object Branches : UUIDTable("Branches") {
    val moduleId = reference("moduleId", Modules.id)
    val name = varchar("name", 64)
    val latestIndexId = reference("latestIndexId", Indexes.id, onDelete = ReferenceOption.RESTRICT)
        .nullable().default(null)

    init {
        uniqueIndex(moduleId, name)
    }
}

object Indexes : UUIDTable("Indexes") {
    val branchId = reference("branchId", Branches.id)
    val commitRef = varchar("commitRef", 40)
    val value = uinteger("value")
    val date = datetime("date")

    init {
        uniqueIndex(branchId, id)
        uniqueIndex(branchId, value)
    }
}