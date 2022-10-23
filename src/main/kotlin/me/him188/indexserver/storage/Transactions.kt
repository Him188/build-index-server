package me.him188.indexserver.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


suspend fun <T> runTransaction(db: Database, statement: Transaction.() -> T): T {
    contract { callsInPlace(statement, InvocationKind.EXACTLY_ONCE) }
    return runInterruptible(Dispatchers.IO) { transaction(db, statement) }
}
