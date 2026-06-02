# CERBER / GeoCamera — Project Dump for LLM

Краткий, плотный дамп проекта для быстрого ввода в контекст нейросети.
Дата: 2026-06-02. Ветка: `main`. Репозиторий: `andreihicatilooo-droid/CERBER`.

---

## 1. Назначение

Android-приложение **GeoCamera** (внутренний код-нейм CERBER, applicationId
`com.aistudio.geocamera.qwert`) — камера, которая делает фото, накладывает на
кадр водяной знак с координатами/временем/точностью GPS, сохраняет в приватное
"секретное хранилище" приложения, ведёт альбом с экспортом в галерею,
позволяет грузить кадры на файлхостинг **nbox.me**, и показывает карту OSM
с ближайшими школами/больницами/полицией/госучреждениями через Google Places.
Доступ ко всему может быть закрыт локальным паролем.

> README в репозитории — шаблон AI Studio и не отражает реальный функционал.

---

## 2. Стек и версии (ключевое)

- **Язык/UI:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.09.00), Material 3,
  Navigation Compose 2.8.9.
- **AGP:** 9.1.1. `minSdk=24`, `targetSdk=36`, `compileSdk=36.1`,
  Java 11.
- **Камера:** CameraX 1.5.0 (`camera-core/camera2/lifecycle/view`).
- **Геолокация:** `play-services-location` 21.3.0 (FusedLocationProvider).
- **Карты:** osmdroid 6.1.18 + Google **Places API** (Nearby Search) через
  Retrofit 2.12 + Moshi 1.15.
- **HTTP:** OkHttp 4.10, logging-interceptor 4.10.
- **БД:** Room 2.7.0 (KSP 2.3.5).
- **Картинки:** Coil 2.7.
- **Permissions:** Accompanist 0.37.3.
- **Тесты:** JUnit 4, Robolectric 4.16, Roborazzi 1.59 (скриншот-тесты).
- **Секреты:** плагин `secrets-gradle-plugin` 2.0.1. Читает `.env`
  (fallback на `.env.example`) и инжектит `MAPS_API_KEY` в манифест
  (`${MAPS_API_KEY}`) и в `BuildConfig.MAPS_API_KEY`.
- **Firebase BOM 34.12** подключён, но `firebase-ai` закомментирован — Gemini
  в рантайме **не вызывается**. `GEMINI_API_KEY` фактически не используется
  в коде, но `.env`/`.env.example` его декларируют.

---

## 3. Структура проекта

```
build.gradle.kts, settings.gradle.kts, gradle.properties
gradle/libs.versions.toml            ← версии и зависимости (version catalog)
.env / .env.example                  ← секреты (MAPS_API_KEY, GEMINI_API_KEY)
debug.keystore.base64                ← debug keystore (base64), декодится в CI
metadata.json                        ← манифест AI Studio
app/
  build.gradle.kts                   ← конфиг приложения, signing, secrets
  proguard-rules.pro
  src/main/
    AndroidManifest.xml              ← permissions, FileProvider, MAPS_API_KEY
    java/com/example/
      MainApplication.kt             ← инициализация Room + PhotoRepository
      MainActivity.kt                ← setContent → AppNavHost; GeoCameraAppUI
      GeoLocationHelper.kt           ← Flow<Location?> + ImageUtils (watermark)
      data/
        AppDatabase.kt               ← PhotoEntity, PhotoDao, AppDatabase
        PhotoRepository.kt           ← CRUD + uploadToNbox
        SettingsManager.kt           ← SharedPreferences (пароль)
      network/
        NetworkClient.kt             ← Retrofit + Moshi singleton для Places
        PlacesApi.kt                 ← Retrofit-интерфейс Google Places
        NboxUploaderService.kt       ← multipart POST на nbox.me + HTML-парсинг
      ui/
        MainViewModel.kt             ← StateFlow allPhotos/currentPhoto/upload
        Screens.kt                   ← AppNavHost, AuthScreen, AlbumScreen, EditPhotoScreen
        MapScreen.kt                 ← osmdroid + Places-маркеры
        theme/                       ← Color/Theme/Type
    res/
      xml/file_paths.xml             ← FileProvider paths
      xml/backup_rules.xml, data_extraction_rules.xml
      values/                        ← strings, colors, themes
  src/test/                          ← Robolectric + Roborazzi screenshot tests
  src/androidTest/                   ← Espresso instrumented test
```

---

## 4. Граф навигации (Compose Navigation)

`AppNavHost` (`Screens.kt`):

- Если в `SettingsManager` задан пароль → сначала `AuthScreen`.
- После разблокировки граф:
  - `camera` (start) → `GeoCameraAppUI` (в `MainActivity.kt`)
  - `album` → `AlbumScreen`
  - `edit/{id:Int}` → `EditPhotoScreen`
  - `map` → `MapScreen`

Переходы запускаются из камеры (кнопки «Карта», «Галерея»), из альбома
(тап по фото) и через системные `back`.

---

## 5. Ключевые экраны и потоки данных

### 5.1 Камера — `GeoCameraAppUI` (`MainActivity.kt`)
- Запрос permissions: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
  через `rememberMultiplePermissionsState`.
- `GeoLocationHelper.getLocationFlow()` отдаёт `Flow<Location?>` от
  `FusedLocationProviderClient` с приоритетом `HIGH_ACCURACY`,
  интервал 2000 мс, минимальный 1000 мс. Используется `callbackFlow` с
  `awaitClose { removeLocationUpdates }`.
- CameraX биндится: `Preview` + `ImageCapture`, `DEFAULT_BACK_CAMERA`,
  привязка к `LocalLifecycleOwner`.
- Оверлеи: «GeoCam» + точность (±м), плашка lat/lng/time, кнопки Map,
  Gallery (миниатюра последнего фото), Shutter, Settings.
- Спуск:
  1. `ImageUtils.createTempFile(context)` → tmp в `externalCacheDir`.
  2. `imageCapture.takePicture(...)` → коллбек `OnImageSavedCallback`.
  3. В `Dispatchers.IO`: `ImageUtils.addWatermarkAndSaveToInternal(...)`.
  4. Полученный URI шлётся в `onImageSaved(uri, lat, lng)`, который
     вызывает `viewModel.insertPhoto(...)` (Room).
  5. tmp-файл удаляется. Тост «Сохранено» / «Ошибка сохранения».

### 5.2 Водяной знак — `ImageUtils` (`GeoLocationHelper.kt`)
- Декодит JPEG в `Bitmap`, рисует Canvas-ом 4 строки в правом-нижнем
  блоке: время `dd.MM.yyyy HH:mm:ss`, `Шир`, `Долг`, `Точность м`.
- Полупрозрачный чёрный фон, белый текст с тенью, размер шрифта =
  3.5% ширины снимка.
- Сохраняет JPEG (quality 95) в `filesDir/secure_vault/GeoCam_<ts>.jpg`,
  возвращает `Uri.fromFile(...)`.
- `exportToGallery(context, uriString)` — копирует в `MediaStore.Images`
  (`Pictures/GeoCam_Export/...`) с обработкой Scoped Storage (API 29+
  через `IS_PENDING`).

### 5.3 Альбом — `AlbumScreen` (`Screens.kt`)
- `viewModel.allPhotos: StateFlow<List<PhotoEntity>>` ← `PhotoDao`
  (`ORDER BY timestamp DESC`).
- `LazyVerticalGrid(3)` с Coil-миниатюрами.
- `combinedClickable`:
  - Long-press → режим выбора, hold-state в `selectedPhotos: Set<Int>`.
  - Tap в режиме выбора → toggle.
  - Tap вне режима → `onPhotoClick(id)` → `edit/{id}`.
- В режиме выбора в TopAppBar — иконка Share → batched
  `ImageUtils.exportToGallery(...)`, по итогу тост «Экспортировано X из Y».

### 5.4 Редактор — `EditPhotoScreen`
- `LaunchedEffect(id)` → `viewModel.loadPhoto(id)` (заполняет
  `currentPhoto: StateFlow<PhotoEntity?>`).
- Поле описания → `viewModel.updatePhotoDescription(id, desc)`.
- Кнопка «В nbox.me» → `viewModel.uploadPhotoToNbox(photo)`.
- `uploadState: StateFlow<String?>` показывается строкой
  ("Загрузка..." / "Успех! Ссылка: ..." / "Ошибка: ...").
- Если `photo.nboxUrl != null` — кликабельная ссылка открывает URL в
  `ACTION_VIEW`.
- Кнопка корзины → `viewModel.deletePhoto(id)` + `popBackStack`.

### 5.5 Карта — `MapScreen` (`MapScreen.kt`)
- Запрос `ACCESS_FINE_LOCATION` (+ `WRITE_EXTERNAL_STORAGE` — избыточно
  для современного osmdroid).
- `osmdroid.Configuration.getInstance().load(context, prefs)`.
- `MapView` с `TileSourceFactory.MAPNIK`, мультитач, zoom 14.
- `MyLocationNewOverlay(GpsMyLocationProvider, mapView)` с
  `enableMyLocation()` и `enableFollowLocation()`.
- При `lastLocation` запускает 4 запроса к Google Places Nearby Search
  (`school`, `hospital`, `police`, `local_government_office`, radius
  2000 м, ключ из `BuildConfig.MAPS_API_KEY`). На каждое место —
  `Marker` с названием и типом.
- Параметры API в `PlacesApi.kt`: `GET place/nearbysearch/json` (base
  URL задан в `NetworkClient`).

### 5.6 Защита паролем — `AuthScreen` + `SettingsManager`
- `SettingsManager` хранит `app_password` в `SharedPreferences`
  `geo_cam_settings` — **в открытом виде** (без хеша, без
  `EncryptedSharedPreferences`).
- `AppNavHost` блокирует доступ к графу, пока пользователь не введёт
  совпадающий пароль. Пустая строка = пароль отключён.
- Меняется через диалог настроек в камере (кнопка-шестерёнка): задать /
  изменить / стереть.

### 5.7 Диалог настроек (`MainActivity.kt`, нижняя половина файла)
- Управление паролем.
- Кнопка «Скачать APK (копировать ссылку)» — кладёт хардкод-URL
  `https://ais-pre-7d7bzzaynft2isgsl52lkd-101130027326.europe-west2.run.app`
  в clipboard.
- Кнопка «Поделиться» — `ACTION_SEND` с тем же URL.

---

## 6. Модель данных (Room)

```kotlin
@Entity(tableName = "photos")
data class PhotoEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val uriString: String,
  val description: String = "",
  val latitude: Double? = null,
  val longitude: Double? = null,
  val timestamp: Long = System.currentTimeMillis(),
  val nboxUrl: String? = null,
)
```

`PhotoDao`: `getAllPhotos(): Flow<List<>>`, `getPhotoById(id)`,
`insertPhoto`, `updatePhoto`, `deletePhotoById`. Версия БД = 1,
`exportSchema = false`.

`MainApplication` создаёт `AppDatabase` (Room.databaseBuilder) и
`PhotoRepository(dao, this)`.

---

## 7. Загрузка на nbox.me — `NboxUploaderService`

- `POST https://nbox.me/put`, `multipart/form-data`, поле `files[]`.
- Батчи по 20 файлов (`files.chunked(20)`).
- Опциональный HTTP-прокси (`host:port`) и кастомные заголовки.
- MIME-тип определяется через `URLConnection.guessContentTypeFromName`.
- Парсинг HTML-ответа регулярками:
  - `https://nbox\.me/i/...` → `directUrl`
  - `https://nbox\.me/delete/...` → `deleteUrl`
  - `https://nbox\.me/t/...` → `thumbUrl`
- Хрупко: при любых правках вёрстки nbox парсер сломается.
- `PhotoRepository.uploadToNbox` копирует URI в `cacheDir/upload_temp.jpg`
  (избыточно для `file://`-URI из secure_vault), грузит, сохраняет
  `directUrl` в `nboxUrl`.

---

## 8. Permissions и манифест

`AndroidManifest.xml`:
- `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
  `INTERNET`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE`
  (используется только для гейтинга карты, по факту не нужен на API 29+).
- `<uses-feature android:name="android.hardware.camera" />`.
- `FileProvider` (`${applicationId}.provider`) с `@xml/file_paths`.
- `meta-data com.google.android.geo.API_KEY = ${MAPS_API_KEY}` —
  подставляется secrets-плагином из `.env`.

---

## 9. Сборка и запуск

- `signingConfigs`:
  - `release` — берёт `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD`
    из env, alias `upload`, файл `my-upload-key.jks` в корне.
  - `debugConfig` — `debug.keystore` в корне, alias `androiddebugkey`,
    пароль `android` (стандартный debug).
- `buildTypes.release.signingConfig = release` — для локальной сборки
  README рекомендует временно удалить эту строку или подставить debug.
- `secrets { propertiesFileName = ".env"; defaultPropertiesFileName = ".env.example" }`.
- Запуск: открыть в Android Studio, прописать `.env`, собрать `:app`.

---

## 10. Окружение и секреты

`.env` (gitignored, см. `.gitignore`):

```
GEMINI_API_KEY=AIzaSyBLFlPh-4dULoB4uVqm1hswjAsaTMC6Owk
MAPS_API_KEY=MY_MAPS_API_KEY
```

- `MAPS_API_KEY` нужен для Maps SDK мета-даты и для Google Places Nearby
  Search; пока заглушка — список ближайших POI не подгрузится
  (`MapScreen` гейтит условием `apiKey != "MY_MAPS_API_KEY"`).
- `GEMINI_API_KEY` в коде сейчас не читается — оставлен для будущего
  включения `firebase-ai` (закомментирован в `app/build.gradle.kts`).

---

## 11. Тесты

- `app/src/test/.../ExampleUnitTest.kt` — sanity.
- `ExampleRobolectricTest.kt` — Robolectric.
- `GreetingScreenshotTest.kt` — Roborazzi скриншот-тест Compose;
  скриншоты лежат в `app/src/test/screenshots/`.
- `app/src/androidTest/.../ExampleInstrumentedTest.kt` — Espresso.

---

## 12. Известные риски / TODO для LLM

1. Пароль хранится plaintext в SharedPreferences → перевести на
   `EncryptedSharedPreferences` или хеш + salt.
2. `secure_vault` — обычная папка `filesDir`, без шифрования. Имя
   вводит в заблуждение.
3. `MapScreen` зря требует `WRITE_EXTERNAL_STORAGE` на современных API.
4. Парсинг HTML nbox.me хрупкий; стоит вынести в адаптер с фоллбэком.
5. README не соответствует коду (упоминает AI Studio и Gemini, тогда как
   фактически Gemini не вызывается).
6. `PhotoRepository.uploadToNbox` делает лишнюю копию `file://`-URI в
   `cacheDir`.
7. Хардкод URL приложения в диалоге настроек.
8. `compileSdk` 36.1 + AGP 9.1 — экспериментальный canary, может
   требовать соответствующей версии Android Studio.

---

## 13. Точки входа для модификаций

| Что менять | Где |
|---|---|
| Поведение спуска / водяной знак | `app/src/main/java/com/example/GeoLocationHelper.kt` (`ImageUtils`) и `MainActivity.kt` (`GeoCameraAppUI`) |
| Схема БД и миграции | `data/AppDatabase.kt`, `MainApplication.kt` |
| Загрузка фото / другой бекенд | `network/NboxUploaderService.kt`, `data/PhotoRepository.kt` |
| Карта/POI | `ui/MapScreen.kt`, `network/PlacesApi.kt`, `network/NetworkClient.kt` |
| Навигация / новые экраны | `ui/Screens.kt` (AppNavHost) |
| Хранение настроек / пароль | `data/SettingsManager.kt`, `ui/Screens.kt` (AuthScreen) |
| Permissions / манифест | `app/src/main/AndroidManifest.xml` |
| Версии библиотек | `gradle/libs.versions.toml` |
| Секреты сборки | `.env`, `app/build.gradle.kts` (`secrets { ... }`) |
