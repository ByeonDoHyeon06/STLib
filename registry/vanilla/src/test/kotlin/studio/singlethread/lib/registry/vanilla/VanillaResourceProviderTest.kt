package studio.singlethread.lib.registry.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class VanillaResourceProviderTest {
    private val provider = VanillaResourceProvider()

    @Test
    fun `ids should include minecraft vanilla item ids`() {
        assertTrue(provider.ids().contains("diamond_sword"))
        assertTrue(provider.ids().contains("stone"))
    }

    @Test
    fun `createItem should support raw and namespaced id`() {
        val sword = provider.createItem("diamond_sword")
        val stone = provider.createItem("minecraft:stone")

        assertEquals(Material.DIAMOND_SWORD, sword?.type)
        assertEquals(Material.STONE, stone?.type)
    }

    @Test
    fun `displayName should return prettified label for vanilla material`() {
        assertEquals("Diamond Sword", provider.displayName("minecraft:diamond_sword"))
        assertNull(provider.displayName("minecraft:not_an_item"))
    }

    @Test
    fun `icon should clone createItem behavior`() {
        val icon = provider.icon("minecraft:golden_apple")
        assertNotNull(icon)
        assertEquals(Material.GOLDEN_APPLE, icon?.type)
    }

    @Test
    fun `resolve item id should map material to vanilla id`() {
        val resolved = provider.resolveItemId(ItemStack(Material.DIAMOND_SWORD))
        assertEquals("diamond_sword", resolved)
    }

    @Test
    fun `block ids should include stone and place or remove block should work`() {
        assertTrue(provider.blockIds().contains("stone"))
    }
}
