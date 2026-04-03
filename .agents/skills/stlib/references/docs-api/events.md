# 이벤트 API

STLib 이벤트 설계는 다음을 동시에 지원합니다.

- ST 표준 경로: `STListener` + `EventRegistrar`
- Bukkit 호환 경로: `org.bukkit.event.Listener` 직접 등록

## API 계약 (`framework:api`)

```kotlin
interface STListener

interface EventRegistrar {
    fun listen(listener: STListener)
    fun unlisten(listener: STListener)
    fun unlistenAll()
}
```

## STPlugin 내장 메소드

`STPlugin`에서 바로 사용 가능합니다.

```kotlin
protected fun listen(listener: STListener)
protected fun listen(listener: org.bukkit.event.Listener)
protected fun unlisten(listener: STListener)
protected fun unlisten(listener: org.bukkit.event.Listener)
protected fun unlistenAll()
protected fun <T : STEvent> fire(event: T): T
```

`onDisable()`에서는 `unlistenAll()`이 자동 호출됩니다.

## STListener 사용 시 주의

Bukkit 플랫폼에서는 `STListener` 인스턴스가 런타임에 `org.bukkit.event.Listener`도 구현해야 합니다.

```kotlin
class JoinListener : STListener, org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler
    fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        // ...
    }
}
```

## 커스텀 STEvent

`STEvent`는 Bukkit `Event`를 상속한 ST 전용 베이스 클래스입니다.

```kotlin
import org.bukkit.event.HandlerList
import studio.singlethread.lib.framework.bukkit.event.STEvent

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

