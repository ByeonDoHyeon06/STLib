package studio.singlethread.lib.framework.bukkit.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class STGuiBuilderDslTest {
    @Test
    fun `set row and column should map to expected slot`() {
        val builder = STGuiBuilder(size = 27)

        builder.set(row = 1, column = 2, item = null)
        val blueprint = builder.build()

        assertEquals(setOf(11), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern should map bound symbols to slots`() {
        val builder = STGuiBuilder(size = 27)

        builder.pattern(
            "ab ",
            " c ",
        ) {
            key('a', null)
            key('b', null)
            key('c', null)
        }

        val blueprint = builder.build()
        assertEquals(setOf(0, 1, 10), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern plus top-level set symbol should map symbol slots`() {
        val builder = STGuiBuilder(size = 27)

        builder.pattern(
            "###",
            "# S",
            "###",
        )
        builder.set('#', null)
        builder.set('S', null)

        val blueprint = builder.build()
        assertEquals(setOf(0, 1, 2, 9, 11, 18, 19, 20), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern set alias should map bound symbols to slots`() {
        val builder = STGuiBuilder(size = 9)

        builder.pattern("xy ") {
            set('x', null)
            set('y', null)
        }

        val blueprint = builder.build()
        assertEquals(setOf(0, 1), blueprint.staticSlots.keys)
    }

    @Test
    fun `set should support multiple slots`() {
        val builder = STGuiBuilder(size = 9)

        builder.set(listOf(1, 3, 5, 7), null)

        val blueprint = builder.build()
        assertEquals(setOf(1, 3, 5, 7), blueprint.staticSlots.keys)
    }

    @Test
    fun `set should support vararg slot shorthand`() {
        val builder = STGuiBuilder(size = 9)

        builder.set(0, 2, 4, 6, item = null)

        val blueprint = builder.build()
        assertEquals(setOf(0, 2, 4, 6), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern should fail on unbound symbol`() {
        val builder = STGuiBuilder(size = 9)

        assertThrows(IllegalArgumentException::class.java) {
            builder.pattern("ax") {
                key('a', null)
            }
        }
    }

    @Test
    fun `pattern should fail when symbols are not mapped before build`() {
        val builder = STGuiBuilder(size = 9)

        builder.pattern("ab")
        builder.set('a', null)

        assertThrows(IllegalArgumentException::class.java) {
            builder.build()
        }
    }

    @Test
    fun `view should register state value specific blueprint`() {
        val builder = STGuiBuilder(size = 54)

        builder.view(
            stateKey = "view",
            stateValue = "list",
        ) {
            set(10, null)
        }

        val blueprint = builder.build()
        assertEquals(1, blueprint.stateViews.size)
        val group = blueprint.stateViews.first()
        assertEquals("view", group.stateKey)
        assertEquals(1, group.views.size)
        assertEquals("list", group.views.first().stateValue.value)
        assertEquals(setOf(10), group.views.first().blueprint.staticSlots.keys)
    }

    @Test
    fun `view should support multiple state values with single definition`() {
        val builder = STGuiBuilder(size = 27)

        builder.view(
            stateKey = "view",
            stateValues = listOf("list", "summary"),
        ) {
            set(5, null)
        }

        val group = builder.build().stateViews.single()
        val values = group.views.map { it.stateValue.value }.toSet()
        assertEquals(setOf("list", "summary"), values)
        group.views.forEach { binding ->
            assertEquals(setOf(5), binding.blueprint.staticSlots.keys)
        }
    }

    @Test
    fun `set should support multiple symbols`() {
        val builder = STGuiBuilder(size = 9)

        builder.pattern("abc")
        builder.setSymbols(symbols = listOf('a', 'c'), item = null)
        builder.empty('b')

        val blueprint = builder.build()
        assertEquals(setOf(0, 2), blueprint.staticSlots.keys)
    }

    @Test
    fun `typed state key should retain configured type`() {
        val key = stateKey<Int>("page")
        val builder = STGuiBuilder(size = 9)

        builder.state(key, 2)
        val blueprint = builder.build()

        assertEquals(Int::class, blueprint.initialState["page"]!!.type)
        assertEquals(2, blueprint.initialState["page"]!!.value)
    }

    @Test
    fun `pattern should reject cells outside inventory size`() {
        val builder = STGuiBuilder(size = 5)

        assertThrows(IllegalArgumentException::class.java) {
            builder.pattern("######")
        }
    }
}
