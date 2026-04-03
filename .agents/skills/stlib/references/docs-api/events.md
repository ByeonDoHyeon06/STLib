# 이벤트 API

현재 STLib(Bukkit) 이벤트 모델은 **Bukkit 이벤트 시스템과 완전 호환**되는 형태입니다.

핵심 구조:

- API 계약: `EventRegistrar` (`framework:api`)
- Bukkit 구현: `BukkitEventRegistrar` (`framework:bukkit`)
- ST 전용 베이스:
  - `STListener<P : STPlugin>` (Bukkit `Listener` 상속)
  - `STEvent` (Bukkit `Event` 상속)

## API 계약 (`framework:api`)

```kotlin
interface EventRegistrar {
    fun listen(listener: Any)
    fun unlisten(listener: Any)
    fun unlistenAll()
}
```

메모:

- 계약은 플랫폼 중립을 위해 `Any`를 받습니다.
- Bukkit 구현(`BukkitEventRegistrar`)에서는 런타임에 `org.bukkit.event.Listener` 여부를 검증합니다.

## `STListener` (plugin 주입형)

```kotlin
abstract class STListener<out P : STPlugin>(
    protected val plugin: P,
) : Listener
```

권장 패턴:

```kotlin
class JoinListener(plugin: MyPlugin) : STListener<MyPlugin>(plugin) {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.announce(event.player, "<green>Welcome</green>")
    }
}
```

## `STPlugin` 이벤트 헬퍼

현재 시그니처(실코드 기준):

```kotlin
protected fun listen(listener: org.bukkit.event.Listener)
protected fun <T : STListener<*>> listen(listenerClass: Class<T>): T
protected inline fun <reified T : STListener<*>> listen(): T

protected fun unlisten(listener: org.bukkit.event.Listener)
protected fun unlistenAll()

protected fun <T : STEvent> fire(event: T): T
```

동작 포인트:

- `listen<T : STListener<*>>()`는 DI(`component<T>()`)로 인스턴스를 생성 후 등록합니다.
- `onDisable()` 경로에서 `unlistenAll()`이 자동 실행되어 누수를 방지합니다.

## 커스텀 `STEvent`

`STEvent`는 Bukkit `Event`를 상속한 ST 전용 베이스입니다.

```kotlin
abstract class STEvent(
    isAsync: Boolean = false,
) : Event(isAsync) {
    abstract override fun getHandlers(): HandlerList
}
```

구현 예시:

```kotlin
class UserSyncEvent(
    val userId: String,
) : STEvent() {
    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
```

발행:

```kotlin
val fired = fire(UserSyncEvent(userId = "abc"))
```

## 주의사항

- Bukkit 플랫폼에서는 등록 대상이 반드시 `org.bukkit.event.Listener`를 구현해야 합니다.
- 커스텀 이벤트는 Bukkit 규약(`companion object HandlerList + getHandlerList`)을 반드시 따라야 합니다.
