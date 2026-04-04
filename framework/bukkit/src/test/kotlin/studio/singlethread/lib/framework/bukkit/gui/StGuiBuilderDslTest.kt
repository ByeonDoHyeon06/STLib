package studio.singlethread.lib.framework.bukkit.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StGuiBuilderDslTest {
    @Test
    fun `set row and column should map to expected slot`() {
        val builder = StGuiBuilder(rows = 3)

        builder.set(row = 1, column = 2, item = null)
        val blueprint = builder.build()

        assertEquals(setOf(11), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern should map bound symbols to slots`() {
        val builder = StGuiBuilder(rows = 3)

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
        val builder = StGuiBuilder(rows = 3)

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
        val builder = StGuiBuilder(rows = 1)

        builder.pattern("xy ") {
            set('x', null)
            set('y', null)
        }

        val blueprint = builder.build()
        assertEquals(setOf(0, 1), blueprint.staticSlots.keys)
    }

    @Test
    fun `set should support multiple slots`() {
        val builder = StGuiBuilder(rows = 1)

        builder.set(listOf(1, 3, 5, 7), null)

        val blueprint = builder.build()
        assertEquals(setOf(1, 3, 5, 7), blueprint.staticSlots.keys)
    }

    @Test
    fun `pattern should fail on unbound symbol`() {
        val builder = StGuiBuilder(rows = 1)

        assertThrows(IllegalArgumentException::class.java) {
            builder.pattern("ax") {
                key('a', null)
            }
        }
    }

    @Test
    fun `pattern should fail when symbols are not mapped before build`() {
        val builder = StGuiBuilder(rows = 1)

        builder.pattern("ab")
        builder.set('a', null)

        assertThrows(IllegalArgumentException::class.java) {
            builder.build()
        }
    }

    @Test
    fun `page should register state value specific blueprint`() {
        val builder = StGuiBuilder(rows = 6)

        builder.page(
            stateKey = "view",
            stateValue = "list",
        ) {
            set(10, null)
        }

        val blueprint = builder.build()
        assertEquals(1, blueprint.statePages.size)
        val group = blueprint.statePages.first()
        assertEquals("view", group.stateKey)
        assertEquals(1, group.pages.size)
        assertEquals("list", group.pages.first().stateValue)
        assertEquals(setOf(10), group.pages.first().blueprint.staticSlots.keys)
    }

    @Test
    fun `page default should register fallback blueprint`() {
        val builder = StGuiBuilder(rows = 6)

        builder.pageDefault(stateKey = "view") {
            set(22, null)
        }

        val blueprint = builder.build()
        assertEquals(1, blueprint.statePages.size)
        val group = blueprint.statePages.first()
        assertEquals("view", group.stateKey)
        assertEquals(emptyList<Any>(), group.pages)
        assertEquals(setOf(22), group.defaultPage!!.staticSlots.keys)
    }
}
