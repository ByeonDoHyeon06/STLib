# 스토리지 API

## `storageApi` vs `storage`

- `storageApi: StorageApi`
  - 여러 설정(`StorageConfig`)으로 저장소 인스턴스를 생성하는 팩토리 API
  - `create(config)`로 필요한 저장소를 추가 생성할 때 사용
- `storage: Storage`
  - 부팅 시 선택된 **기본 저장소 인스턴스**
  - 일반 플러그인 로직은 대부분 이것부터 사용

`STPlugin`에서는 둘 다 기본 제공됩니다.

## 핵심 인터페이스

```kotlin
interface StorageApi : AutoCloseable {
    fun create(config: StorageConfig): Storage
}

interface Storage : AutoCloseable {
    fun collection(name: String): CollectionStorage
    fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult>
    fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?>
    fun remove(query: Query): CompletableFuture<Boolean>
    fun exists(query: Query): CompletableFuture<Boolean>
    fun <T> setSync(query: Query, data: T, codec: StorageCodec<T>): WriteResult
    fun <T> getSync(query: Query, codec: StorageCodec<T>): T?
    fun removeSync(query: Query): Boolean
    fun existsSync(query: Query): Boolean
}
```

## 사용 예제

### 컬렉션 기반 저장

```kotlin
import studio.singlethread.lib.storage.api.extensions.get
import studio.singlethread.lib.storage.api.extensions.set

val users = collection("users")
users.set("uuid-1", mapOf("level" to 10))
    .thenAccept { result ->
        logger.info("saved: ${result.action} (${result.durationMs}ms)")
    }

users.get<Map<String, Int>>("uuid-1")
    .thenAccept { data ->
        logger.info("loaded: $data")
    }
```

### Query 기반 저장

```kotlin
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.extensions.set

storage.set(Query("users", "uuid-1"), mapOf("coins" to 100))
```

## 지원 백엔드

- JSON
- SQLite
- MySQL
- PostgreSQL

부팅 시 `config/storage.yml`의 `backend`를 우선 사용하고, capability/드라이버 상태에 따라 fallback이 적용됩니다.

## sync 호출 주의사항

`setSync/getSync/removeSync/existsSync`는 Bukkit 메인 스레드에서 호출하면 `StorageMainThreadSyncException`이 발생합니다.

관련 예외:

- `StorageMainThreadSyncException`
- `StorageTimeoutException`
- `StorageSerializationException`
- `StorageBackendException`

## 코덱

기본 코덱 레지스트리(`DefaultCodecRegistry`)가 다음을 자동 처리합니다.

- `String`
- `ByteArray`
- `kotlinx.serialization` 직렬화 가능 타입

필요하면 `StorageCodec<T>`를 직접 전달해 커스텀 직렬화도 가능합니다.

