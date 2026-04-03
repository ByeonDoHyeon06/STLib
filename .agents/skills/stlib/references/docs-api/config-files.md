# 자동 생성 설정 파일

`STPlugin` 부팅 시 `PluginFileConfigLoader`가 아래 파일을 자동 로드/생성/보정합니다.

- `plugins/<PluginName>/config/plugin.yml`
- `plugins/<PluginName>/config/storage.yml`
- `plugins/<PluginName>/config/depend.yml`
- `plugins/<PluginName>/config/bridge.yml`
- `plugins/<PluginName>/config/translation.yml`
- `plugins/<PluginName>/translation/en_us.yml` (및 필요 locale 번들)

기본값 없는 키는 채워서 재저장됩니다.

## `plugin.yml`

```yaml
debug: false
version: ""
```

핵심 포인트:

- `version`이 비어 있으면 `STPlugin(version = "...")` 또는 플러그인 메타 버전을 fallback으로 사용
- `debug=true`면 STPlugin debug 로깅 활성

## `storage.yml`

```yaml
backend: JSON
namespace: ""
syncTimeoutSeconds: 5
executorThreads: 4
json:
  filePath: data/storage.json
sqlite:
  filePath: data/storage.db
mysql:
  host: 127.0.0.1
  port: 3306
  database: minecraft
  username: root
  password: change-me
postgresql:
  host: 127.0.0.1
  port: 5432
  database: minecraft
  username: postgres
  password: change-me
```

핵심 포인트:

- namespace 자동 정규화
- timeout/thread 최소값 보정
- 사용 불가 backend는 자동 fallback

## `depend.yml`

```yaml
runtime:
  loadDatabaseDrivers: true
  loadRedisBridge: true
integrations:
  itemsAdder: true
  oraxen: true
  nexo: true
  mmoItems: true
  ecoItems: true
  placeholderApi: true
```

핵심 포인트:

- `loadDatabaseDrivers=false` => DB 드라이버 런타임 로딩 스킵
- `loadRedisBridge=false` => Redis bridge 런타임 로딩 스킵
- `integrations.*=false` => 해당 integration capability OFF

## `bridge.yml`

```yaml
mode: LOCAL # LOCAL | REDIS | COMPOSITE
namespace: stlib
nodeId: auto
requestTimeoutMillis: 3000
redis:
  address: redis://127.0.0.1:6379
  username: ""
  password: ""
  database: 0
  connectTimeoutMillis: 3000
```

핵심 포인트:

- `mode=REDIS/COMPOSITE`일 때 Redisson 연결 시도
- 실패하면 local degrade + capability reason 기록
- `nodeId=auto`면 `<plugin>-<port>` 기반 자동 ID 생성

## `translation.yml`

```yaml
defaultLocale: en_us
fallbackLocale: ""
```

핵심 포인트:

- locale 정규화: 소문자 + `_`
- fallback chain: requested/sender -> default -> fallback -> en_us
- 누락 키: `!key!` + warn-once

## `stlib.yml` (STLib 운영 설정)

```yaml
version: 1
dashboard:
  enabled: true
  profile: core_ops
  persistStats: false
  flushIntervalSeconds: 30
metrics:
  command:
    enabled: false
```

핵심 포인트:

- `persistStats=false`면 저장 없이 읽기 중심 관제
- `metrics.command.enabled=false`면 계측만 no-op, 명령 기능은 유지

## Versioning/Migration

커스텀 config는 `VersionedConfig + configMigrationPlan`으로 진화시킵니다.

- `registerConfig(..., migrationPlan)` / `reloadConfig(..., migrationPlan)` => migration 적용
- `loadConfig(...)` => raw load (migration 미적용)
