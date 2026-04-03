# 자동 생성 설정 파일

`STPlugin` 부팅 시 `PluginFileConfigLoader`가 아래 파일을 자동 로드/생성합니다.

- `plugins/<PluginName>/config/storage.yml`
- `plugins/<PluginName>/config/depend.yml`
- `plugins/<PluginName>/config/translation.yml`
- `plugins/<PluginName>/translation/en_us.yml` (및 필요 locale 번들)

기본값이 없는 키는 자동으로 채워지고 다시 저장됩니다.

구현 기반:

- Config 파일 입출력은 `ConfigurateConfigService` (`org.spongepowered:configurate-yaml`)로 처리됩니다.
- YAML 로더는 block style + default copy 옵션으로 동작합니다.

## `storage.yml` 기본 구조

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
  parameters:
    useSSL: "false"
    serverTimezone: UTC
postgresql:
  host: 127.0.0.1
  port: 5432
  database: minecraft
  username: postgres
  password: change-me
  parameters: {}
```

### 핵심 포인트

- `namespace`가 비어 있으면 플러그인명 기반으로 자동 정규화됩니다.
- 잘못된 timeout/thread 값은 최소값으로 보정됩니다.
- 요청한 `backend`가 사용 불가면 자동 fallback(`json`)이 적용됩니다.

## `depend.yml` 기본 구조

```yaml
runtime:
  loadDatabaseDrivers: true
integrations:
  itemsAdder: true
  oraxen: true
  nexo: true
  mmoItems: true
  ecoItems: true
  placeholderApi: true
```

### 핵심 포인트

- `runtime.loadDatabaseDrivers = false`면 Libby DB 드라이버 로딩을 건너뜁니다.
- `integrations.* = false`면 해당 integration capability가 비활성화됩니다.
  - 리소스: `itemsAdder/oraxen/nexo/mmoItems/ecoItems`
  - 텍스트: `placeholderApi` (`text:placeholderapi`)
- 비활성화되더라도 코어 프레임워크 부팅은 유지됩니다.

## `translation.yml` 기본 구조

```yaml
defaultLocale: en_us
fallbackLocale: ""
```

### 핵심 포인트

- locale는 소문자 + `_` 형식으로 정규화됩니다. (`ko-KR` -> `ko_kr`)
- 조회 체인은 `sender/requested -> defaultLocale -> fallbackLocale -> en_us`입니다.
- 누락 키는 `!key!` 반환 + warn-once 정책으로 기록됩니다.

## STLib 운영 설정 (`stlib.yml`)

`framework:core`의 STLib 중앙 운영 플러그인은 아래 런타임 설정을 사용합니다.

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

- `dashboard.persistStats = false`면 누적 통계 저장을 생략하고 읽기 중심 GUI만 동작합니다.
- `metrics.command.enabled = false`면 명령 통계 계측은 no-op이며 명령 기능 자체는 유지됩니다.

## Versioning/Migration (선택)

STPlugin/consumer의 커스텀 config(`config/<name>.yml`)는 버전형 마이그레이션을 적용할 수 있습니다.

1. config 클래스가 `VersionedConfig`를 구현하고 `version` 필드를 가집니다.
2. `configMigrationPlan(latestVersion)`에 step 체인을 선언합니다.
3. `registerConfig(fileName, migrationPlan = ...)` 또는 `reloadConfig(fileName, migrationPlan = ...)`으로 로드합니다.

동작:

- 플랜 적용 시 이전 파일이 `config/<...>/.backup/`에 백업됩니다.
- step이 누락되었거나 config version이 최신보다 높으면 fail-fast로 중단됩니다.
- `loadConfig(...)` 직접 호출 경로는 migration plan을 적용하지 않습니다.
