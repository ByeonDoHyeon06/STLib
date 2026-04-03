# 리소스 통합 API

STLib는 리소스 플러그인을 공통 포맷으로 다루기 위해 `ResourceService`를 제공합니다.

현재 기본 provider:

- `minecraft` (Vanilla)
- `itemsadder`
- `oraxen`
- `nexo`
- `mmoitems`
- `ecoitems`

## 핵심 타입

```kotlin
interface ResourceService : ResourceItems {
    fun registerProvider(provider: ResourceProvider)
    fun unregisterProvider(providerId: String): Boolean

    fun providers(): Collection<ResourceProvider>
    fun availableProviders(): Collection<ResourceProvider>

    fun items(): ResourceItems
    fun blocks(): ResourceBlocks
    fun furnitures(): ResourceFurnitures

    // legacy aliases
    fun resolve(refOrId: String): ResourceItemRef?
    fun createItem(refOrId: String): ItemStack?
}
```

```kotlin
interface ResourceItems {
    fun from(refOrId: String): ResourceItemRef?
    fun from(itemStack: ItemStack): ResourceItemRef?

    fun ids(): Collection<String>
    fun displayName(refOrId: String): String?
    fun icon(refOrId: String): ItemStack?
    fun create(refOrId: String): ItemStack?
    fun exists(refOrId: String): Boolean
}
```

```kotlin
interface ResourceBlocks {
    fun from(refOrId: String): ResourceBlockRef?
    fun from(block: Block): ResourceBlockRef?

    fun ids(): Collection<String>
    fun displayName(refOrId: String): String?
    fun icon(refOrId: String): ItemStack?

    fun place(refOrId: String, block: Block): Boolean
    fun remove(block: Block): Boolean
    fun exists(refOrId: String): Boolean
}
```

```kotlin
interface ResourceFurnitures {
    fun from(refOrId: String): ResourceFurnitureRef?
    fun from(entity: Entity): ResourceFurnitureRef?

    fun ids(): Collection<String>
    fun displayName(refOrId: String): String?
    fun icon(refOrId: String): ItemStack?

    fun place(refOrId: String, location: Location): Boolean
    fun remove(entity: Entity): Boolean
    fun exists(refOrId: String): Boolean
}
```

```kotlin
interface ExternalResourceProvider : ResourceProvider {
    val upstreamPluginName: String
    fun refreshState()
    fun onUpstreamPluginEnabled()
    fun onUpstreamPluginDisabled()
    fun onUpstreamDataLoaded()
    fun unavailableReason(): String?
}
```

## Ref 타입

```kotlin
data class ResourceItemRef(val provider: String, val id: String) {
    val namespacedId: String get() = "$provider:$id"
}

data class ResourceBlockRef(val provider: String, val id: String) {
    val namespacedId: String get() = "$provider:$id"
}

data class ResourceFurnitureRef(val provider: String, val id: String) {
    val namespacedId: String get() = "$provider:$id"
}
```

## ID 규칙

- 권장: `provider:id` (`minecraft:diamond_sword`, `itemsadder:my_sword`)
- provider 생략 시: 사용 가능한 provider 목록에서 첫 매칭으로 resolve

## 사용 예제

### 아이템

```kotlin
val items = resourceService.items()

val swordRef = items.from("minecraft:diamond_sword")
val byStackRef = items.from(ItemStack(Material.DIAMOND_SWORD))
val allItemIds = items.ids()
val itemDisplay = items.displayName("oraxen:ruby_sword")
val itemIcon = items.icon("itemsadder:my_item")
val created = items.create("nexo:custom_pickaxe")
val exists = items.exists("minecraft:stone")
```

### 블록

```kotlin
val blocks = resourceService.blocks()

val blockRef = blocks.from("minecraft:stone")
val currentRef = blocks.from(targetBlock)
val allBlockIds = blocks.ids()
val blockDisplay = blocks.displayName("minecraft:stone")
val blockIcon = blocks.icon("minecraft:stone")

val placed = blocks.place("minecraft:stone", targetBlock)
val removed = blocks.remove(targetBlock)
```

### 가구

```kotlin
val furnitures = resourceService.furnitures()

val furnitureRef = furnitures.from("nexo:oak_chair")
val entityRef = furnitures.from(clickedEntity)
val allFurnitureIds = furnitures.ids()
val furnitureDisplay = furnitures.displayName("oraxen:modern_lamp")
val furnitureIcon = furnitures.icon("itemsadder:chair")

val spawned = furnitures.place("nexo:oak_chair", location)
val deleted = furnitures.remove(clickedEntity)
```

### Legacy 경로(호환)

```kotlin
val ref = resourceService.resolve("minecraft:diamond_sword")
val icon = resourceService.icon("minecraft:diamond_sword")
val item = resourceService.createItem("minecraft:diamond_sword")
```

## Capability 연동

각 provider는 설치 여부/설정에 따라 capability가 자동 반영됩니다.

- `resource:minecraft`
- `resource:itemsadder`
- `resource:oraxen`
- `resource:nexo`
- `resource:mmoitems`
- `resource:ecoitems`

`config/depend.yml`에서 특정 외부 통합을 꺼두면 해당 capability는 OFF 처리됩니다.
`minecraft` provider는 기본 내장 provider로 항상 등록됩니다.

## 권장 로드 시점 반영

- ItemsAdder: `ItemsAdderLoadDataEvent` 수신 전까지 대기 상태로 간주
- Nexo: `NexoItemsLoadedEvent` 수신 전까지 대기 상태로 간주
- Oraxen: API probe 기반으로 registry 준비 상태를 판별

STLib는 upstream plugin enable/disable 및 로드 이벤트를 감지해 capability를 즉시 갱신합니다.
