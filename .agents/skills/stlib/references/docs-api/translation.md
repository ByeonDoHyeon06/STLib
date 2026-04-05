# 번역/i18n API

STLib는 `TextService`와 분리된 `TranslationService`를 제공합니다.

- `TextService`: MiniMessage 렌더링
- `TranslationService`: 키 조회/fallback/reload

## 핵심 계약

```kotlin
interface TranslationService {
    fun translate(
        key: String,
        locale: String? = null,
        placeholders: Map<String, String> = emptyMap(),
    ): String

    fun reload()
}
```

## 파일 구조

- `plugins/<PluginName>/config/translation.yml`
- `plugins/<PluginName>/translation/{locale}.yml`

추가로, 플러그인 JAR의 `src/main/resources/translation/*.yml`에 번역 파일을 넣어두면
부팅 시 누락된 파일만 `plugins/<PluginName>/translation/`으로 자동 복사됩니다(기존 파일은 덮어쓰지 않음).

`translation.yml` 기본 스키마:

```yaml
defaultLocale: en_us
fallbackLocale: ""
```

## fallback 규칙

조회 체인:

1. 요청 locale (또는 sender locale)
2. `defaultLocale`
3. `fallbackLocale` (설정된 경우)
4. `en_us`

키를 끝까지 찾지 못하면 `!key!`를 반환하고, 동일 `(key + locale chain)`에 대해 경고 로그는 1회만 출력합니다.

## STPlugin 헬퍼

- `translate(key, placeholders)`
- `translate(sender, key, placeholders)`
- `sendTranslated(sender, key, placeholders)`
- `reloadTranslations()`

번역 문자열은 MiniMessage로 파싱되어 `Component`로 전송됩니다.

## 예시

```kotlin
override fun enable() {
    command("hello") {
        executes { ctx ->
            val sender = ctx.sender as? org.bukkit.command.CommandSender ?: return@executes
            sendTranslated(sender, "example.welcome", mapOf("player" to sender.name))
        }
    }
}
```
