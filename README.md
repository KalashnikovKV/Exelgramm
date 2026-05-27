# Exelgramm

Чат поверх Google Таблицы через Apps Script.

## Настройка Google (один раз)

1. Откройте таблицу → **Расширения → Apps Script**.
2. Вставьте код из `docs/apps-script/Code.gs`, сохраните.
3. **Развернуть → Новое развёртывание → Веб-приложение**  
   - Выполнять от имени: **Я**  
   - Доступ: **Все**  
4. Скопируйте URL вида `https://script.google.com/macros/s/.../exec`.

После обновления `Code.gs`: **Развернуть → Управление развёртываниями → ✏️ → Новая версия → Развернуть**.

Если сообщения не загружаются: в таблице **Доступ → Все с ссылкой → Читатель** (для запасного чтения CSV).
5. В листе (например `Лист1`) строка 1: `id` | `timestamp` | `author` | `text`.

## Приложение

1. При первом запуске сразу открывается экран чата.
2. Перейдите на вкладку **Профиль**, введите имя, ссылку на таблицу и URL веб-приложения → **Сохранить**.
3. Отправка и обновление сообщений каждые 5 секунд (pull-to-refresh тоже работает).

Тестовая таблица: `https://docs.google.com/spreadsheets/d/13ho5j5noB5wHl6Iaj71PwwKf1GJuS34qT6-_ol5Crsc/edit`

## Сборка приложения

### Требования

| Инструмент | Версия |
|---|---|
| JDK | 11+ |
| Android Gradle Plugin | 9.2.1 |
| compileSdk | 36 (ext. 1) |
| minSdk | 24 (Android 7.0) |

### Debug APK (быстро, без подписи)

```bash
./gradlew assembleDebug
```

APK окажется в `app/build/outputs/apk/debug/app-debug.apk`.

### Release APK

1. Создайте keystore (один раз):

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias exelgramm \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

2. Добавьте в `gradle.properties` (или в переменные окружения):

```properties
STORE_FILE=../release.jks
STORE_PASSWORD=ваш_пароль
KEY_ALIAS=exelgramm
KEY_PASSWORD=ваш_пароль
```

3. В `app/build.gradle.kts` в блок `android { }` добавьте:

```kotlin
signingConfigs {
    create("release") {
        storeFile     = file(project.property("STORE_FILE") as String)
        storePassword = project.property("STORE_PASSWORD") as String
        keyAlias      = project.property("KEY_ALIAS") as String
        keyPassword   = project.property("KEY_PASSWORD") as String
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = false
    }
}
```

4. Соберите:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`.

### Установка на устройство / эмулятор

```bash
# debug
adb install app/build/outputs/apk/debug/app-debug.apk

# release
adb install app/build/outputs/apk/release/app-release.apk
```

### Сборка через Android Studio

**Build → Build App Bundle(s) / APK(s) → Build APK(s)** — готовый файл появится в той же директории `app/build/outputs/apk/`.