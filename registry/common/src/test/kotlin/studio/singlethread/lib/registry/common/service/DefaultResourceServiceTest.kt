package studio.singlethread.lib.registry.common.service

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.registry.common.capability.ResourceCapability
import studio.singlethread.lib.registry.common.provider.ResourceBlockProvider
import studio.singlethread.lib.registry.common.provider.ResourceFurnitureProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider

class DefaultResourceServiceTest {
    @Test
    fun `resolve should support explicit provider namespace`() {
        val service = DefaultResourceService(
            listOf(
                fakeProvider("itemsadder", setOf("my_item")),
                fakeProvider("oraxen", setOf("other_item")),
            ),
        )

        assertEquals("itemsadder:my_item", service.resolve("itemsadder:my_item")?.namespacedId)
        assertEquals("oraxen:other_item", service.resolve("other_item")?.namespacedId)
        assertNull(service.resolve("missing"))
    }

    @Test
    fun `register and unregister should update provider set`() {
        val service = DefaultResourceService()
        val provider = fakeProvider("itemsadder", setOf("my_item"))

        service.registerProvider(provider)
        assertEquals(1, service.providers().size)

        val removed = service.unregisterProvider("itemsadder")
        assertTrue(removed)
        assertEquals(0, service.providers().size)
    }

    @Test
    fun `items facade should support from itemstack and create`() {
        val service = DefaultResourceService(
            listOf(
                fakeItemProvider(
                    providerId = "minecraft",
                    ids = setOf("diamond_sword"),
                    resolver = { stack ->
                        if (stack.type == Material.DIAMOND_SWORD) "diamond_sword" else null
                    },
                ),
            ),
        )

        val itemRef = service.items().from(ItemStack(Material.DIAMOND_SWORD))
        assertEquals("minecraft:diamond_sword", itemRef?.namespacedId)
        assertTrue(service.items().exists("minecraft:diamond_sword"))
        assertEquals(Material.DIAMOND_SWORD, service.items().create("minecraft:diamond_sword")?.type)
    }

    @Test
    fun `block and furniture facades should resolve by namespaced ref`() {
        val service = DefaultResourceService(
            listOf(
                fakeBlockProvider(providerId = "oraxen", ids = setOf("ruby_block")),
                fakeFurnitureProvider(providerId = "nexo", ids = setOf("oak_chair")),
            ),
        )

        assertEquals("oraxen:ruby_block", service.blocks().from("oraxen:ruby_block")?.namespacedId)
        assertEquals("nexo:oak_chair", service.furnitures().from("nexo:oak_chair")?.namespacedId)
    }

    private fun fakeProvider(providerId: String, ids: Set<String>): ResourceProvider {
        return object : ResourceProvider {
            override val providerId: String = providerId

            override fun isAvailable(): Boolean = true

            override fun capabilities(): Set<ResourceCapability> = ResourceCapability.entries.toSet()

            override fun ids(): Collection<String> = ids

            override fun displayName(id: String): String? = id

            override fun icon(id: String): ItemStack? = null

            override fun createItem(id: String): ItemStack? = null
        }
    }

    private fun fakeItemProvider(
        providerId: String,
        ids: Set<String>,
        resolver: (ItemStack) -> String?,
    ): ResourceItemProvider {
        return object : ResourceItemProvider {
            override val providerId: String = providerId

            override fun isAvailable(): Boolean = true

            override fun capabilities(): Set<ResourceCapability> = ResourceCapability.entries.toSet()

            override fun ids(): Collection<String> = ids

            override fun displayName(id: String): String? = id

            override fun icon(id: String): ItemStack? = ItemStack(Material.DIAMOND_SWORD)

            override fun createItem(id: String): ItemStack? = ItemStack(Material.DIAMOND_SWORD)

            override fun resolveItemId(itemStack: ItemStack): String? = resolver(itemStack)
        }
    }

    private fun fakeBlockProvider(providerId: String, ids: Set<String>): ResourceBlockProvider {
        return object : ResourceBlockProvider {
            override val providerId: String = providerId

            override fun isAvailable(): Boolean = true

            override fun capabilities(): Set<ResourceCapability> = ResourceCapability.entries.toSet()

            override fun ids(): Collection<String> = emptyList()

            override fun displayName(id: String): String? = id

            override fun icon(id: String): ItemStack? = null

            override fun createItem(id: String): ItemStack? = null

            override fun blockIds(): Collection<String> = ids

            override fun resolveBlockId(block: org.bukkit.block.Block): String? = null

            override fun placeBlock(id: String, block: org.bukkit.block.Block): Boolean = false

            override fun removeBlock(block: org.bukkit.block.Block): Boolean = false
        }
    }

    private fun fakeFurnitureProvider(providerId: String, ids: Set<String>): ResourceFurnitureProvider {
        return object : ResourceFurnitureProvider {
            override val providerId: String = providerId

            override fun isAvailable(): Boolean = true

            override fun capabilities(): Set<ResourceCapability> = ResourceCapability.entries.toSet()

            override fun ids(): Collection<String> = emptyList()

            override fun displayName(id: String): String? = id

            override fun icon(id: String): ItemStack? = null

            override fun createItem(id: String): ItemStack? = null

            override fun furnitureIds(): Collection<String> = ids

            override fun resolveFurnitureId(entity: org.bukkit.entity.Entity): String? = null

            override fun placeFurniture(id: String, location: org.bukkit.Location): Boolean = false

            override fun removeFurniture(entity: org.bukkit.entity.Entity): Boolean = false
        }
    }
}
