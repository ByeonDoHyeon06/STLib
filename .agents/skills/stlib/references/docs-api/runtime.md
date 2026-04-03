# 런타임 서비스 API

STLib는 Bukkit 런타임에서 아래 공통 서비스를 기본 등록합니다.

- `NotifierService`
- `SchedulerService`
- `BridgeService` (in-memory)
- `InventoryUiService`

## NotifierService

```kotlin
interface NotifierService {
    fun message(template: String, placeholders: Map<String, String> = emptyMap()): Component
    fun prefixed(template: String, placeholders: Map<String, String> = emptyMap()): Component
    fun send(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun sendPrefixed(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun actionBar(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun title(target: Audience, titleTemplate: String, subtitleTemplate: String = "", placeholders: Map<String, String> = emptyMap())
}
```

`STPlugin` 헬퍼:

- `tell(...)`
- `announce(...)`
- `tellTranslated(...)`
- `actionBar(...)`
- `title(...)`
- `broadcast(...)`
- `broadcastTranslated(...)`

메모:

- `STPlugin.send(sender, ...)`와 `mini(sender, ...)`는 sender 컨텍스트를 사용하며 `integrations.placeholderApi=true` + PlaceholderAPI 설치 시 `%placeholder%` 치환을 함께 적용합니다.

## SchedulerService

```kotlin
interface SchedulerService {
    fun runSync(task: Runnable): ScheduledTask
    fun runAsync(task: Runnable): ScheduledTask
    fun runLater(delayTicks: Long, task: Runnable): ScheduledTask
    fun runTimer(delayTicks: Long, periodTicks: Long, task: Runnable): ScheduledTask
    fun <T> callSync(task: Callable<T>): CompletableFuture<T>
}
```

`STPlugin` 헬퍼:

- `sync { ... }`
- `async { ... }`
- `later(delayTicks) { ... }`
- `timer(delayTicks, periodTicks) { ... }`

## BridgeService

기본 구현은 `InMemoryBridgeService`입니다.
분산 브리지(예: Redis)는 후속 백엔드로 확장할 수 있습니다.

```kotlin
interface BridgeService : AutoCloseable {
    fun publish(channel: String, payload: String)
    fun subscribe(channel: String, listener: BridgeListener): BridgeSubscription
}
```

`STPlugin` 헬퍼:

- `publish(channel, payload)`
- `subscribe(channel) { ch, payload -> ... }`
- `unsubscribe(subscription)`

## Inventory UI

`InventoryUiService`는 간단한 메뉴 빌더를 제공합니다.

- `ui.menu(rows, title) { ... }`
- `ui.open(player, menu)`
- 메뉴 매핑은 reopen 시 자동 재바인딩되며, 마지막 viewer가 닫힐 때만 매핑이 정리됩니다.

예시:

```kotlin
val menu = ui.menu(rows = 3, title = "<gold>Demo") {
    slot(13, ItemStack(Material.DIAMOND)) { click ->
        click.whoClicked.sendMessage(mini("<green>clicked"))
    }
}
ui.open(player, menu)
```
