package studio.singlethread.lib.framework.bukkit.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STInject
import studio.singlethread.lib.framework.api.di.STScope
import studio.singlethread.lib.framework.core.kernel.DefaultSTKernel

class ReflectiveComponentResolverTest {
    @Test
    fun `resolve should inject owner type`() {
        val resolver = resolver(owner = TestOwner())

        val component = resolver.resolve(OwnerAwareComponent::class.java)

        assertEquals("owner", component.owner.name)
    }

    @Test
    fun `resolve should inject kernel service`() {
        val kernel = DefaultSTKernel().apply {
            registerService(TextDependency::class, TextDependency("from-kernel"))
        }
        val resolver = ReflectiveComponentResolver(owner = TestOwner(), kernel = kernel)

        val component = resolver.resolve(NeedsServiceComponent::class.java)

        assertEquals("from-kernel", component.dependency.value)
    }

    @Test
    fun `singleton component should be reused`() {
        val resolver = resolver(owner = TestOwner())

        val first = resolver.resolve(SingletonComponent::class.java)
        val second = resolver.resolve(SingletonComponent::class.java)

        assertSame(first, second)
    }

    @Test
    fun `prototype component should create new instance`() {
        val resolver = resolver(owner = TestOwner())

        val first = resolver.resolve(PrototypeComponent::class.java)
        val second = resolver.resolve(PrototypeComponent::class.java)

        assertNotSame(first, second)
    }

    @Test
    fun `inject annotation should choose intended constructor`() {
        val kernel = DefaultSTKernel().apply {
            registerService(TextDependency::class, TextDependency("via-inject"))
        }
        val resolver = ReflectiveComponentResolver(owner = TestOwner(), kernel = kernel)

        val component = resolver.resolve(InjectedConstructorComponent::class.java)

        assertEquals("via-inject", component.value)
    }

    @Test
    fun `field injection should resolve dependencies`() {
        val kernel = DefaultSTKernel().apply {
            registerService(TextDependency::class, TextDependency("field"))
        }
        val resolver = ReflectiveComponentResolver(owner = TestOwner(), kernel = kernel)

        val component = resolver.resolve(FieldInjectedComponent::class.java)

        assertEquals("field", component.dependency.value)
    }

    @Test
    fun `circular dependency should throw`() {
        val resolver = resolver(owner = TestOwner())

        assertThrows(IllegalStateException::class.java) {
            resolver.resolve(CircularA::class.java)
        }
    }

    private fun resolver(owner: TestOwner): ReflectiveComponentResolver {
        return ReflectiveComponentResolver(owner = owner, kernel = DefaultSTKernel())
    }

    private data class TestOwner(
        val name: String = "owner",
    )

    private class TextDependency(
        val value: String,
    )

    private class OwnerAwareComponent(
        val owner: TestOwner,
    )

    private class NeedsServiceComponent(
        val dependency: TextDependency,
    )

    @STComponent(scope = STScope.SINGLETON)
    private class SingletonComponent

    @STComponent(scope = STScope.PROTOTYPE)
    private class PrototypeComponent

    private class InjectedConstructorComponent {
        val value: String

        constructor() {
            value = "default"
        }

        @STInject
        constructor(dependency: TextDependency) {
            value = dependency.value
        }
    }

    private class FieldInjectedComponent {
        @STInject
        lateinit var dependency: TextDependency
    }

    private class CircularA(
        @Suppress("unused")
        val b: CircularB,
    )

    private class CircularB(
        @Suppress("unused")
        val a: CircularA,
    )
}

