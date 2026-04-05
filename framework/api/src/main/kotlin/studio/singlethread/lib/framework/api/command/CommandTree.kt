package studio.singlethread.lib.framework.api.command

fun interface CommandTree {
    fun CommandDslBuilder.define()
}
