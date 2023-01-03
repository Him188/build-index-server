package me.him188.indexserver.cli

class Command(
    val name: String,
    val description: String,
    val invoke: suspend context(CommandContext) (args: List<String>) -> Unit,
)

interface CommandManager {
    val commands: Collection<Command>

    fun register(command: Command): Boolean

    suspend fun executeCommandLine(line: String)
}

internal class CommandManagerImpl : CommandManager {
    override val commands: MutableCollection<Command> = mutableListOf()

    override fun register(command: Command): Boolean {
        if (commands.any { it.name == command.name }) return false
        commands.add(command)
        return true
    }

    private object CommandContextImpl : CommandContext {
        override suspend fun echo(text: String) {
            println(text)
        }
    }

    override suspend fun executeCommandLine(line: String) {
        val split = line.split(" ")
        if (split.isEmpty()) {
            return
        }
        val name = split.first()
        val args = split.drop(1)
        val command = commands.find { it.name == name } ?: throw IllegalArgumentException("Command '$name' not found.")
        command.invoke(CommandContextImpl, args)
    }
}

interface CommandContext {
    suspend fun echo(text: String)
}