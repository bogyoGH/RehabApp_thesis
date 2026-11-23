# Sportolói mozgás- és terhelésadatok elemzését támogató felhőalapú alkalmazás

Ez a repozitórium a **„Sportolói mozgás- és terhelésadatok elemzését támogató felhőalapú alkalmazás fejlesztése”** című szakdolgozathoz tartozó mobilalkalmazás forráskódját és az adatbázis kialakításához használt SQL-állományokat tartalmazza.

A rendszer Kotlin Multiplatform (KMP) és Compose Multiplatform alapú mobilalkalmazásból, valamint Supabase-alapú (PostgreSQL, Storage, Auth) háttérrendszerből áll. Célja az egyensúlyozási és rehabilitációs gyakorlatok során keletkező mozgás- és terhelésadatok gyűjtése, tárolása, feldolgozása és megjelenítése. Az adatgyűjtés Sensoria okoszoknik Bluetooth Low Energy (BLE) kapcsolatán és okostelefonos videórögzítésen alapul; a kiértékelés egy része adatbázisoldali eljárásokkal történik.

> **Fontos – forráskód és reprodukálhatóság:** A nyilvános repozitórium nem tartalmazza a fejlesztői környezet Supabase-végpontját és klienskonfigurációját (`SupabaseClient.kt`), az esetleges helyi `.env` fájlokat, illetve a valódi mérési videókat és JSON-adatállományokat. A hiányzó Kotlin-fájlt az alábbi útmutató szerint kell pótolni. **A letöltött projekt jelenlegi formájában nem fordítható változtatás nélkül**, és a teljes működéshez külön konfigurált háttérrendszer, megfelelő hozzáférési szabályok, egyes funkciókhoz pedig megfelelő hardver és külső feldolgozó szolgáltatások szükségesek. A mellékelt minta nem jelent ellenőrzött, teljes körű telepítési receptet.

## Főbb funkciók

- **Felhasználókezelés és profil:** e-mail/jelszó alapú regisztráció és bejelentkezés Supabase Auth segítségével; antropometriai adatok (életkor, nem, magasság, testsúly) kezelése.
- **Gyakorlatkatalógus:** globális és egyéni gyakorlatok létrehozása, szerkesztése, törlése, valamint oktatóvideók kezelése a Supabase Storage használatával.
- **Párhuzamos BLE-adatgyűjtés:** két Sensoria okoszokni GATT-kapcsolatának kezelése; 20 bájtos bináris adatcsomagok dekódolása. A feldolgozott adatok között talpi nyomás, gyorsulás, giroszkóp- és magnetométer-adatok szerepelnek.
- **Videórögzítés és mérésvezérlés:** előlapi kamerás HD-videórögzítés Androidon (CameraX), előkészületi és mérési visszaszámláló, szüneteltetés és leállítás, továbbá szintetikus szenzoradatok előállítása a `MockDataGenerator` segítségével.
- **Helyi mentés és szinkronizáció:** mérési adatok tárolása JSON-fájlokban; Androidon WorkManager-alapú, hálózati feltételhez kötött háttérszinkronizáció.
- **Eredménymegjelenítés:** biomechanikai össz- és részpontszámok (stabilitás, alátámasztás, szimmetria), törzsdőlés-idősor, talpi nyomáseloszlás és CoP-proxy pálya, időbeli fejlődést mutató diagramok, logikai törlés és visszaállítás.
- **Szerveroldali kiértékelés:** PostgreSQL/PL/pgSQL eljárások és triggerek; védett adatbázisséma, RLS-alapú hozzáférés-kezelés és publikus API-nézetek.

## Rendszerarchitektúra

```text
Sensoria okoszoknik (BLE) ──┐
                           ├──> KMP / Android mobilalkalmazás
Telefon kamerája (CameraX) ┘    (MVVM, Compose, WorkManager)
                                       |
                                       | HTTPS / Supabase API
                                       v
                               Supabase háttérrendszer
                               ├── Auth
                               ├── Storage
                               │   ├── measurement-videos
                               │   └── exercise-videos
                               └── PostgreSQL
                                   ├── public API-nézetek
                                   ├── védett `zokni` séma és RLS
                                   └── PL/pgSQL kiértékelő triggerek

Külső videófeldolgozás: NiFi + MMPose
(A videófeldolgozó folyamat külön komponens;
a reprodukálásához saját telepítés és konfiguráció szükséges.)
```

## Projektstruktúra

- [`composeApp/src/commonMain`](./composeApp/src/commonMain): megosztott Kotlin-kód, Compose felületek, állapotkezelés, adatmodellek és platformfüggetlen deklarációk.
- [`composeApp/src/androidMain`](./composeApp/src/androidMain): Android-specifikus megvalósítások (BLE, kamera, helyi fájlok, háttérszinkronizáció).
- [`composeApp/src/iosMain`](./composeApp/src/iosMain): iOS-specifikus forráskód, amennyiben az adott funkcióhoz implementáció áll rendelkezésre.
- [`iosApp`](./iosApp): iOS alkalmazásindító projekt.
- `supabase/`: a háttérrendszerhez tartozó SQL-állományok és egyéb szerveroldali fájlok (a tényleges fájlszerkezet szerint).
- `gradlew`, `gradlew.bat` és a Gradle-projektfájlok: projektépítés és függőségkezelés.

### Megosztott réteg (`commonMain`)

#### Felhasználói felület és navigáció (`org.example.project.ui`)

| Fájl | Feladat |
|---|---|
| `App.kt` | Alkalmazásindítás, Material 3 téma, `NavHost`, alsó navigáció és bejelentkezési állapot figyelése. |
| `LoginScreen.kt` | Regisztráció és bejelentkezés, profiladatok bekérése. |
| `ConfigScreen.kt` | Gyakorlatkatalógus, időtartam és Bluetooth-kapcsolat beállítása. |
| `ExerciseFormScreen.kt` | Egyéni gyakorlatok és oktatóvideók kezelése. |
| `ExecutionScreen.kt` | Kamerakép, visszaszámlálók, szüneteltetés és mérésmentés. |
| `ResultsScreen.kt` | Pontszámok, törzsdőlés-grafikon, talpi elemzés és CoP-proxy megjelenítése. |
| `ProgressScreen.kt` | Korábbi mérések fejlődési diagramjai. |
| `SavedSessionScreen.kt` | Mentett és logikailag törölt mérések kezelése, visszaállítása. |
| `ProfileScreen.kt` | Profiladatok megjelenítése és módosítása. |
| `CameraPreview.kt`, `VideoPlayer.kt`, `VideoPicker.kt` | Platformfüggő kamerás és videós funkciók megosztott deklarációi. |

#### Állapotkezelés (`org.example.project.viewmodel`)

- `SessionViewModel.kt`: a munkamenet teljes életciklusának vezérlése; időzítés, BLE- és szimulált adatgyűjtés, mérési dokumentum összeállítása, feltöltés és eredményszinkronizáció.
- `SessionUiState.kt`: a felhasználói felület aktuális állapotát reprezentáló immutable adatosztály.

#### Hálózati réteg (`org.example.project.network`)

- `AuthRepository.kt`: regisztráció, bejelentkezés, kijelentkezés és sportolói profil létrehozása.
- `ProfileRepository.kt`: profiladatok lekérdezése és frissítése.
- `ExerciseRepository.kt`: gyakorlatok kezelése, helyi `exercises_cache.json` gyorsítótár és oktatóvideók.
- `SessionRepository.kt`: mérési munkamenetek helyi/felhős kezelése, videófeltöltés, `pending`/`sock_only` állapotok, logikai törlés, visszaállítás, `restore_session` és `get_my_sock_analysis` RPC-hívások, kiértékelések lekérdezése.

#### Bluetooth és jelfeldolgozás (`org.example.project.bluetooth`)

- `SensoriaBluetoothManager.kt`: platformfüggetlen `expect` deklaráció a BLE-kapcsolathoz és a `Flow<Pair<SockSide, SensorSample>>` adatfolyamhoz.
- `BitReader.kt`: különböző bitszélességű adatok kiolvasása bájttömbből.
- `SensoriaDecoder.kt`: 20 bájtos Sensoria-keretek átalakítása `SensorSample` objektummá.
- `SockSide.kt`: bal és jobb oldali zokni megkülönböztetése.

#### Adatmodellek, DTO-k és segédosztályok

- `MeasurementDocument.kt`: a helyben mentett és a szerverre küldött mérési JSON felépítése.
- `SensoriaData.kt`: `SensorSample`, `Pressure`, `IMU`, `Magnetometer` adatmodellek.
- `Athlete.kt`, `Exercises.kt`, `ScheduledTask.kt`, `SessionEntity.kt`: alap entitások és ütemezési modellek.
- `BiomechanicsResult.kt`, `TrunkAnglePoint.kt`, `SockAnalysisResult.kt`: a szerveroldali eredmények deszerializálási modelljei.
- `FileStorage.kt`, `SyncManager.kt`, `Platform.kt`: platformfüggetlen deklarációk a fájlkezeléshez, szinkronizációhoz és platformazonosításhoz.
- `MockDataGenerator.kt`: szintetikus szenzoradatok hardver nélküli kipróbáláshoz.

### Android-specifikus réteg (`androidMain`)

- `MainActivity.kt`, `AppContextHolder.kt`: Android belépési pont és az alkalmazáskontextus kezelése.
- `SensoriaBluetoothManager.kt`: natív BLE-eszközkeresés, GATT-kapcsolatok, CCCD-értesítések és `host_time_ms` időbélyegek kezelése.
- `CameraPreview.android.kt`: CameraX-alapú előnézet és MP4-videórögzítés.
- `VideoPlayer.android.kt`, `VideoPicker.android.kt`: Media3 ExoPlayer és rendszeres videóválasztó integrációja.
- `FileStorage.android.kt`: helyi JSON-fájlok kezelése a `context.filesDir` könyvtárban; szinkronizációs jelölés.
- `SyncManager.android.kt`, `SessionSyncWorker.kt`: WorkManager/`CoroutineWorker`-alapú háttérszinkronizáció, `NetworkType.CONNECTED` hálózati feltétellel.
- `Platform.android.kt`: Android-platformadatok szolgáltatása.

**Platformmegjegyzés:** A KMP-projekt tartalmaz iOS-hez kapcsolódó projektstruktúrát, de a README-ben felsorolt CameraX- és WorkManager-funkciók Android-specifikusak. E dokumentáció nem állítja, hogy az Androidon bemutatott összes funkció teljes egészében tesztelt vagy elérhető iOS-en.

## Adatbázis és szerveroldali kiértékelés

A dokumentált PostgreSQL-struktúra két réteget használ:

1. **Védett belső `zokni` séma:** a tényleges táblák, például `athletes`, `exercises`, `sessions`, `video_json`, `extracted_keypoints`, `biomechanics_results`, `sock_analysis_results`, `combined_analysis_results`. A hozzáférés-ellenőrzésben Row Level Security (RLS) szabályok vesznek részt.
2. **Publikus `public` API-réteg:** `security_invoker` nézetek (például `athletes`, `exercises`, `sessions`, `deleted_sessions`, `biomechanics_results`) és szabályozott RPC-függvények (például `restore_session`, `get_my_sock_analysis`). Egyes írási és logikai törlési műveleteket `INSTEAD OF` triggerek kezelnek.

### Automatizált kiértékelés

| Trigger / folyamat | Feladat |
|---|---|
| `trg_after_video_json_insert` | A videós pózbecslésből származó anatómiai kulcspontok átemelése az `extracted_keypoints` táblába. |
| `trg_calc_biomechanics` | `analysis_status = 'inserted'` esetén szűrés, anatómiai normalizálás, de Leva-alapú CoM-becslés, poszturális jellemzők és törzsszög-idősor előállítása. |
| `trigger_evaluate_sock_on_inserted` | Zokniadatok elemzése videós feldolgozást követően, illetve `sock_only` mérési állapotban. |
| `trigger_combined_on_video`, `trigger_combined_on_sock` | Videós és talpi mérési eredmények összevetése a két modalitás rendelkezésre állásakor. |

A külső NiFi–MMPose videófeldolgozó pipeline külön rendszerelem. A teljes rendszer helyi újratelepítéséhez a pipeline és a megfelelő adatbázis-/tárhelyszabályok konfigurálása is szükséges.

## Konfiguráció és futtatás

### Előfeltételek

- Kompatibilis JDK és Android SDK, Android Studio vagy megfelelő Gradle-környezet.
- A projektben definiált Gradle-függőségek letöltéséhez hálózati hozzáférés.
- Saját Supabase-projekt és adatbázis; az SQL-állományok alkalmazásához megfelelő jogosultságok.
- A felhős funkciókhoz megfelelő Auth-, Storage- és adatbázis-beállítások.
- BLE-méréshez kompatibilis Sensoria okoszokni; kamerás méréshez megfelelő Android-eszköz és engedélyek.
- iOS-alkalmazás indításához macOS, Xcode és az iOS-célplatformhoz szükséges fejlesztői környezet.

### 1. Supabase klienskonfiguráció pótlása

A nyilvános repozitóriumból a környezetspecifikus adatokat tartalmazó `SupabaseClient.kt` fájl hiányzik. A fordításhoz az eredeti osztály- és csomagstruktúrával egyező fájlt kell létrehozni az alábbi helyen:

```text
composeApp/src/commonMain/kotlin/org/example/project/network/SupabaseClient.kt
```

A következő kód **konfigurációs minta**, nem tartalmaz valódi végpontot vagy kulcsot:

```kotlin
package org.example.project.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {
    private const val SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
    private const val SUPABASE_KEY = "YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY"

    val supabaseClient = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
        install(Postgrest)
        install(Storage)
        install(Auth)
    }
}
```

A `SUPABASE_URL` értékét a saját projekt URL-jére, a kulcsot pedig a saját kliensoldali Supabase-kulcsra kell cserélni. **A `service_role` és más titkos, szerveroldali kulcsok nem kerülhetnek a mobilalkalmazásba.** A kliensoldali kulcs használatához megfelelő RLS-, Storage- és Auth-szabályokat kell beállítani. Ha a háttérrendszer saját IP-címen érhető el, a végpontot a konkrét környezetnek megfelelően kell módosítani; Androidon a hálózati elérhetőség és a HTTP/HTTPS szabályai külön ellenőrzendők.

A fájl helyét és az `object SupabaseManager` elnevezést a meglévő importokhoz kell igazítani. A példa nem garantálja, hogy további környezeti beállítások nélkül minden funkció működni fog.

### 2. Adatbázis és Storage előkészítése

1. Hozz létre saját Supabase-projektet.
2. Vizsgáld át és szükség esetén sorrendben alkalmazd a repozitóriumban található SQL-állományokat. A fájlok nem feltétlenül alkotnak önmagukban automatikusan futtatható, teljes migrációs láncot.
3. Hozd létre és konfiguráld a `measurement-videos` és `exercise-videos` Storage bucketeket.
4. Ellenőrizd az RLS-szabályokat, RPC-függvényeket, triggereket és a bejelentkezett felhasználó jogosultságait.
5. A videóalapú elemzéshez külön konfiguráld a NiFi–MMPose feldolgozó folyamatot, ha azt is futtatni szeretnéd.

### 3. Android-alkalmazás fordítási parancsai

Linux és macOS:

```bash
./gradlew :composeApp:assembleDebug
```

Windows (PowerShell):

```powershell
.\gradlew.bat :composeApp:assembleDebug
```

A parancsok a debug APK fordítását kezdeményezik; a hiányzó konfiguráció és egyéb szükséges környezeti feltételek pótlása nélkül a fordítás hibával megszakadhat. **A repozitórium változtatás nélküli sikeres fordítása nincs igazolva.**

### 4. iOS-projekt

Az [`iosApp`](./iosApp) könyvtár Xcode-ban nyitható meg. Az iOS-célplatformhoz további környezeti és platformfüggő implementációk lehetnek szükségesek. A jelen README nem tekinthető teljes iOS-telepítési útmutatónak.

## Technológiák

| Terület | Eszközök |
|---|---|
| Kliens | Kotlin Multiplatform, Compose Multiplatform, Material 3, Kotlin Coroutines és Flow, `kotlinx.serialization`, `kotlinx.datetime` |
| Android | BLE/GATT API, CameraX, Media3 ExoPlayer, AndroidX WorkManager |
| Backend és adattárolás | Supabase (Auth, Storage, PostgreSQL), supabase-kt, Ktor |
| Adatbázisoldali elemzés | PostgreSQL, PL/pgSQL, RLS, triggerek |
| Külső videós feldolgozás | NiFi, MMPose (külön konfigurálandó) |

## Adatvédelem és a nyilvános forráskód korlátai

- A nyilvános repozitórium nem tartalmazza a fejlesztői környezet tényleges végpontját és egyedi konfigurációját.
- Nem tartalmaz valós személyekhez köthető mérési videókat vagy JSON-mérési állományokat.
- Az adatokhoz való hozzáférést nem a kliensoldali kulcs titkossága, hanem elsősorban a helyes hitelesítés, az RLS és a Storage-hozzáférési szabályok védik.
- A szenzoros mérés, a felhős szolgáltatások és a videófeldolgozó rendszer teljes reprodukálásához a közzétett forráskódon túl további konfiguráció és eszközök szükségesek.
- Éles környezetben kizárólag megfelelő engedélyekkel és adatkezelési feltételekkel szabad személyes mérési adatokat feldolgozni.

## Kapcsolódó dokumentáció

A repozitórium a szakdolgozati megvalósításhoz tartozik. A rendszer elvi hátterét, tervezését, az alkalmazott biomechanikai módszereket és az eredmények értelmezését a szakdolgozat részletesebben tárgyalja.