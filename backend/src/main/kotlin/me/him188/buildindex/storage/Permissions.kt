package me.him188.buildindex.storage

import me.him188.buildindex.storage.BranchPermission.*
import me.him188.buildindex.storage.IndexPermission.*
import me.him188.buildindex.storage.ModulePermission.*


typealias Permission = String

inline fun composePermissions(action: context(PermissionsBuilder) () -> Unit): Set<Permission> {
    val builder = PermissionsBuilder()
    action.invoke(builder)
    return builder.build()
}

inline fun composeSinglePermission(action: context(PermissionBuilder) () -> PermissionBuilder.Composed<*>): Permission {
    val builder = PermissionBuilder()
    action.invoke(builder)
    val build = builder.build()
    return build.singleOrNull() ?: throw IllegalStateException("Multiple permissions composed: ${build.joinToString()}")
}

//fun main() {
//    composePermissions {
//        anyModule().anyBranch().all()
//        module("111").branch("1231")
//            .indexRead()
//            .indexWrite()
//
//    }
//    composeSinglePermission {
//        anyModule().anyBranch().all()
//    }
//}

open class PermissionBuilder(
    private val delegate: String = ""
) {
    private val container = mutableSetOf<Permission>()

    val root get() = Root

    interface PermissionNode {
        val delegate: String

        fun createPermission(child: String): String {
            return "$delegate.$child"
        }
    }

    object Root : PermissionNode {
        override val delegate: String
            get() = ""

        override fun createPermission(child: String): String = child
    }

    @JvmInline
    value class WithBranch(
        override val delegate: String
    ) : PermissionNode

    @JvmInline
    value class Composed<T : PermissionNode>(
        override val delegate: String
    ) : PermissionNode

    @JvmInline
    value class WithModule(
        override val delegate: String
    ) : PermissionNode


    fun module(module: String): WithModule = WithModule(delegate + module)

    fun anyModule(): WithModule = WithModule("$delegate.*")


    fun WithModule.branch(name: String): WithBranch = WithBranch("$delegate.$name")
    fun WithModule.anyBranch(): WithBranch = WithBranch("$delegate.*")


    @DslMarker
    protected annotation class PermissionBuilderDsl


    protected fun PermissionNode.addPermission(
        token: PermissionToken,
    ) {
        for (include in token.includes) {
            addPermission(include)
        }
        token.token?.let {
            container.add(createPermission(it))
        }
    }

    protected inline fun WithBranch.compose(block: WithBranch.() -> Unit): Composed<WithBranch> {
        block()
        return Composed(this.delegate)
    }

    protected inline fun Root.compose(block: Root.() -> Unit): Composed<Root> {
        block()
        return Composed(delegate)
    }

    protected inline fun WithModule.compose(block: WithModule.() -> Unit): Composed<WithModule> {
        block()
        return Composed(this.delegate)
    }

    @PermissionBuilderDsl
    fun WithBranch.token(token: IndexPermission): Composed<WithBranch> {
        return when (token) {
            INDEX_LIST -> indexList()
            INDEX_LATEST -> indexLatest()
            INDEX_READ -> indexRead()
            INDEX_NEXT -> indexNext()
            INDEX_DELETE -> indexDelete()
            INDEX_WRITE -> indexWrite()
            INDEX -> index()
        }
    }

    @PermissionBuilderDsl
    fun Root.token(token: ModulePermission): Composed<Root> {
        return when (token) {
            MODULE_LIST -> moduleList()
            MODULE_CREATE -> moduleCreate()
            MODULE_DELETE -> moduleDelete()
            MODULE -> moduleAll()
        }
    }

    @PermissionBuilderDsl
    fun WithModule.token(token: BranchPermission): Composed<WithModule> {
        return when (token) {
            BRANCH_LIST -> branchList()
            BRANCH_CREATE -> branchCreate()
            BRANCH_DELETE -> branchDelete()
            BRANCH -> branchAll()
        }
    }

    @PermissionBuilderDsl
    fun WithBranch.indexList(): Composed<WithBranch> = compose { addPermission(INDEX_LIST) }

    @PermissionBuilderDsl
    fun WithBranch.indexLatest(): Composed<WithBranch> = compose { addPermission(INDEX_LATEST) }

    @PermissionBuilderDsl
    fun WithBranch.indexRead(): Composed<WithBranch> = compose { addPermission(INDEX_READ) }

    @PermissionBuilderDsl
    fun WithBranch.indexNext(): Composed<WithBranch> = compose { addPermission(INDEX_NEXT) }

    @PermissionBuilderDsl
    fun WithBranch.indexDelete(): Composed<WithBranch> = compose { addPermission(INDEX_DELETE) }

    @PermissionBuilderDsl
    fun WithBranch.indexWrite(): Composed<WithBranch> = compose { addPermission(INDEX_WRITE) }

    @PermissionBuilderDsl
    fun WithBranch.index(): Composed<WithBranch> = compose { addPermission(INDEX) }


    @PermissionBuilderDsl
    fun Root.moduleList(): Composed<Root> = compose { addPermission(MODULE_LIST) }

    @PermissionBuilderDsl
    fun Root.moduleCreate(): Composed<Root> = compose { addPermission(MODULE_CREATE) }

    @PermissionBuilderDsl
    fun Root.moduleDelete(): Composed<Root> = compose { addPermission(MODULE_DELETE) }

    @PermissionBuilderDsl
    fun Root.moduleAll(): Composed<Root> = compose { addPermission(MODULE) }


    @PermissionBuilderDsl
    fun WithModule.branchList(): Composed<WithModule> = compose { addPermission(BRANCH_LIST) }

    @PermissionBuilderDsl
    fun WithModule.branchCreate(): Composed<WithModule> = compose { addPermission(BRANCH_CREATE) }

    @PermissionBuilderDsl
    fun WithModule.branchDelete(): Composed<WithModule> = compose { addPermission(BRANCH_DELETE) }

    @PermissionBuilderDsl
    fun WithModule.branchAll(): Composed<WithModule> = compose { addPermission(BRANCH) }


    fun build(): Set<Permission> = container.toSet()
}

class PermissionsBuilder(delegate: String = "") : PermissionBuilder(delegate) {
    private inline fun Composed<WithBranch>.compose(block: WithBranch.() -> Unit): Composed<WithBranch> {
        block(WithBranch(delegate))
        return Composed(delegate)
    }

    @JvmName("composeWithModule")
    private inline fun Composed<WithModule>.compose(block: WithModule.() -> Unit): Composed<WithModule> {
        block(WithModule(delegate))
        return Composed(delegate)
    }

    @JvmName("composeRoot")
    private inline fun Composed<Root>.compose(block: Root.() -> Unit): Composed<Root> {
        block(Root)
        return Composed(delegate)
    }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.token(token: IndexPermission): Composed<WithBranch> {
        return when (token) {
            INDEX_LIST -> indexList()
            INDEX_LATEST -> indexLatest()
            INDEX_READ -> indexRead()
            INDEX_NEXT -> indexNext()
            INDEX_DELETE -> indexDelete()
            INDEX_WRITE -> indexWrite()
            INDEX -> index()
        }
    }

    @PermissionBuilderDsl
    fun Composed<Root>.token(token: ModulePermission): Composed<Root> {
        return when (token) {
            MODULE_LIST -> moduleList()
            MODULE_CREATE -> moduleCreate()
            MODULE_DELETE -> moduleDelete()
            MODULE -> moduleAll()
        }
    }

    @PermissionBuilderDsl
    fun Composed<WithModule>.token(token: BranchPermission): Composed<WithModule> {
        return when (token) {
            BRANCH_LIST -> branchList()
            BRANCH_CREATE -> branchCreate()
            BRANCH_DELETE -> branchDelete()
            BRANCH -> branchAll()
        }
    }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexList(): Composed<WithBranch> = compose { addPermission(INDEX_LIST) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexLatest(): Composed<WithBranch> = compose { addPermission(INDEX_LATEST) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexRead(): Composed<WithBranch> = compose { addPermission(INDEX_READ) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexNext(): Composed<WithBranch> = compose { addPermission(INDEX_NEXT) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexDelete(): Composed<WithBranch> = compose { addPermission(INDEX_DELETE) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.indexWrite(): Composed<WithBranch> = compose { addPermission(INDEX_WRITE) }

    @PermissionBuilderDsl
    fun Composed<WithBranch>.index(): Composed<WithBranch> = compose { addPermission(INDEX) }


    @PermissionBuilderDsl
    fun Composed<Root>.moduleList(): Composed<Root> = compose { addPermission(MODULE_LIST) }

    @PermissionBuilderDsl
    fun Composed<Root>.moduleCreate(): Composed<Root> = compose { addPermission(MODULE_CREATE) }

    @PermissionBuilderDsl
    fun Composed<Root>.moduleDelete(): Composed<Root> = compose { addPermission(MODULE_DELETE) }

    @PermissionBuilderDsl
    fun Composed<Root>.moduleAll(): Composed<Root> = compose { addPermission(MODULE) }


    @PermissionBuilderDsl
    fun Composed<WithModule>.branchList(): Composed<WithModule> = compose { addPermission(BRANCH_LIST) }

    @PermissionBuilderDsl
    fun Composed<WithModule>.branchCreate(): Composed<WithModule> = compose { addPermission(BRANCH_CREATE) }

    @PermissionBuilderDsl
    fun Composed<WithModule>.branchDelete(): Composed<WithModule> = compose { addPermission(BRANCH_DELETE) }

    @PermissionBuilderDsl
    fun Composed<WithModule>.branchAll(): Composed<WithModule> = compose { addPermission(BRANCH) }

}

sealed interface PermissionToken {
    val includes: Array<out PermissionToken>
    val token: String?
}

enum class ModulePermission(
    override vararg val includes: ModulePermission,
    override val token: String? = null,
) : PermissionToken {
    MODULE_LIST("module-list"),
    MODULE_CREATE("module-create"),
    MODULE_DELETE("module-delete"),

    MODULE(MODULE_LIST, MODULE_CREATE, MODULE_DELETE),

    ;

    constructor(token: String) : this(includes = emptyArray(), token = token)
}

enum class BranchPermission(
    override vararg val includes: BranchPermission,
    override val token: String? = null,
) : PermissionToken {
    BRANCH_LIST("branch-list"),
    BRANCH_CREATE("branch-create"),
    BRANCH_DELETE("branch-delete"),

    BRANCH(BRANCH_LIST, BRANCH_CREATE, BRANCH_DELETE),

    ;

    constructor(token: String) : this(includes = emptyArray(), token = token)
}

enum class IndexPermission(
    override vararg val includes: IndexPermission,
    override val token: String? = null,
) : PermissionToken {
    INDEX_LIST("index-list"),
    INDEX_LATEST("index-latest"),
    INDEX_READ(INDEX_LIST, INDEX_LATEST),

    INDEX_NEXT("index-next"),
    INDEX_DELETE("index-delete"),
    INDEX_WRITE(INDEX_NEXT, INDEX_DELETE),

    INDEX(INDEX_READ, INDEX_WRITE),
    ;

    constructor(token: String) : this(includes = emptyArray(), token = token)
}
