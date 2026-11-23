This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

Sportolói mozgás- és terhelésadatok elemzését támogató felhőalapú alkalmazás

Ez a repozitórium a „Sportolói mozgás- és terhelésadatok elemzését támogató felhőalapú alkalmazás fejlesztése” című
szakdolgozathoz tartozó forráskódokat és adatbázis-sémát tartalmazza.

A rendszer egy Kotlin Multiplatform (KMP) / Jetpack Compose alapú mobilalkalmazásból és egy Supabase (PostgreSQL +
Storage + Auth) alapú felhőinfrastruktúrából áll. Célja az egyensúlyozási és rehabilitációs gyakorlatok objektív mérése
Sensoria okoszoknik (Bluetooth Low Energy) és okostelefonos HD videórögzítés együttes használatával, valamint az adatok
szerveroldali, adatbázis-szintű biomechanikai kiértékelése.

Megjegyzés: Biztonsági és adatvédelmi (GDPR) okokból ez a repozitórium nem tartalmazza a szerver eléréséhez szükséges
hitelesítő adatokat (SupabaseClient.kt / .env), a lokális build-fájlokat, valamint a valós sportolói mérési videókat és
JSON-állományokat.

Főbb funkciók

Felhasználókezelés és profil: E-mail/jelszó alapú regisztráció és bejelentkezés (Supabase Auth), sportolói
antropometriai adatok (életkor, nem, magasság, testsúly) kezelése.

Gyakorlatkatalógus: Globális és saját, egyéni gyakorlatok kezelése, oktatóvideók kiválasztása és feltöltése (Supabase
Storage).

Párhuzamos BLE adatgyűjtés: Bal és jobb oldali Sensoria okoszoknik szimultán GATT-kapcsolatkezelése és 20 bájtos (160
bites) bináris adatcsomagok valós idejű dekódolása (3 talpi nyomásszenzor, 3-tengelyes gyorsulásmérő, giroszkóp és
magnetométer).

Videórögzítés és mérésvezérlés: Előlapi kamerás HD videórögzítés (CameraX), felkészülési és mérési visszaszámláló,
szüneteltetés/leállítás, valamint offline szimulációs mód (MockDataGenerator) szenzor hiányában.

Offline-first működés: A mérések helyi JSON-fájlba mentése és hálózatkimaradás esetén automatikus, hálózathoz kötött
háttérszinkronizáció (WorkManager).

Eredményvizualizáció és haladáskövetés: Biomechanikai össz- és részpontszámok (stabilitás, alátámasztás, szimmetria),
görgethető törzsdőlés-idősor grafikon, talpi CoP-proxy pálya megjelenítése, időbeli fejlődési diagramok, valamint soft
delete és visszaállítás.

Szerveroldali kiértékelés és biztonság: Védett zokni séma, security_invoker publikus nézetek, sorszintű
hozzáférés-vezérlés (RLS) és eseményvezérelt PL/pgSQL kiértékelő triggerek.

Rendszerarchitektúra

[ Sensoria Okoszoknik (BLE) ] ──┐
├──► [ KMP / Android Mobilalkalmazás ]
[ Telefon Kamera (CameraX) ] ───┘ │  (MVVM, Jetpack Compose,
│ WorkManager offline sync)
▼ HTTPS (REST / Storage API)
[ Supabase Backend ]
├── Auth (JWT)
├── Storage (measurement-videos, exercise-videos)
└── PostgreSQL (public nézetek ──► zokni séma + RLS)
│
├──► Külső videófeldolgozó pipeline (NiFi + MMPose)
└──► PL/pgSQL Triggerek (CoM, CoP-proxy, kombinált elemzés)

Projektstruktúra és fájlok leírása

1. Közös réteg (commonMain)

Felhasználói felület és navigáció (org.example.project.ui)

App.kt: Az alkalmazás belépési pontja. Definiálja a Material 3 sötét témát, a NavHost navigációs gráfot, az alsó
navigációs sávot (NavigationBar), valamint figyeli a Supabase hitelesítési állapotát (SessionStatus).

LoginScreen.kt: Bejelentkezési és regisztrációs képernyő; regisztrációkor bekéri a sportoló antropometriai adatait is.

ConfigScreen.kt: Főképernyő a gyakorlatok kategóriák szerinti böngészéséhez. A ModalBottomSheet panelen állítható be a
feladat időtartama és a Bluetooth-kapcsolat a mérés indítása előtt.

ExerciseFormScreen.kt: Egyéni gyakorlatok létrehozása, szerkesztése, törlése és oktatóvideók csatolása.

ExecutionScreen.kt: A mérés végrehajtási képernyője kameraképpel (CameraPreview), előkészületi és mérési időzítővel,
szünet/leállítás vezérléssel, valamint mentés/elvetés dialógussal.

ResultsScreen.kt: A kiértékelt mérések részletes nézete: biomechanikai összpontszám, stabilitás/alátámasztás/szimmetria
kártyák, TrunkAngleChart (törzsszög-idősor), valamint SockAnalysisCard és FootCopPlot (talpi nyomáseloszlás és CoP-proxy
pálya).

ProgressScreen.kt: A kiértékelt mérések időbeli alakulását megjelenítő képernyő (ProgressLineChart), gyakorlatonként és
választható metrikánként csoportosítva.

SavedSessionScreen.kt: Az aktív és a logikailag törölt (deleted_sessions) munkamenetek listázása, eredmények megnyitása,
soft delete és visszaállítás.

ProfileScreen.kt: A bejelentkezett sportoló profiladatainak lekérdezése és módosítása.

CameraPreview.kt, VideoPlayer.kt, VideoPicker.kt: Platformfüggetlen expect UI-deklarációk a kamerakezeléshez,
videólejátszáshoz és galériából történő videóválasztáshoz.

Állapotkezelés (org.example.project.viewmodel)

SessionViewModel.kt: MVVM ViewModel, amely vezérli a mérés teljes életciklusát (visszaszámlálók, szüneteltetés, BLE és
mock adatgyűjtés, MeasurementDocument összeállítása, videófeltöltés, felhős listák és eredmények szinkronizálása).

SessionUiState.kt: A felhasználói felület aktuális állapotát leíró megváltoztathatatlan (immutable) adatosztály.

Hálózati réteg (org.example.project.network)

AuthRepository.kt: Regisztráció (automatikus beléptetéssel és athletes profil létrehozásával), bejelentkezés és
kijelentkezés.

ProfileRepository.kt: A sportolói profil lekérdezése és frissítése a public.athletes nézeten keresztül.

ExerciseRepository.kt: Gyakorlatok lekérdezése (helyi exercises_cache.json offline gyorsítótárral), saját gyakorlatok
CRUD műveletei és oktatóvideók kezelése az exercise-videos bucketben.

SessionRepository.kt: Mérések helyi és felhős mentése (pending / sock_only státuszkezeléssel), measurement-videos
feltöltés, soft delete, törölt mérések lekérdezése, restore_session és get_my_sock_analysis RPC-hívások, valamint
biomechanikai eredmények letöltése.

Bluetooth és jelfeldolgozás (org.example.project.bluetooth)

SensoriaBluetoothManager.kt: Platformfüggetlen expect class a BLE kapcsolathoz és a Flow<Pair<SockSide, SensorSample>>
adatfolyamhoz.

BitReader.kt: Segédosztály tetszőleges bitszélességű előjel nélküli értékek kiolvasásához bájttömbből.

SensoriaDecoder.kt: A 20 bájtos bináris Sensoria keretek dekódolása SensorSample objektummá.

SockSide.kt: A bal és jobb oldali szenzor megkülönböztetésére szolgáló enum.

Adatmodellek, DTO-k és segédosztályok (org.example.project.data, dto, analysis)

MeasurementDocument.kt: A felhőbe küldött és lokálisan mentett mérési JSON szerkezete (MeasurementDocument, Sensors,
Side, Video).

SensoriaData.kt: SensorSample, Pressure, IMU, Magnetometer adatmodellek.

Athlete.kt, Exercises.kt, ScheduledTask.kt, SessionEntity.kt: Alap entitások és ütemezett feladattípusok.

BiomechanicsResult.kt, TrunkAnglePoint.kt, SockAnalysisResult.kt: A szerveroldali videós és zoknis elemzések
deszerializálási modelljei.

FileStorage.kt, SyncManager.kt, Platform.kt: Platformfüggetlen expect deklarációk fájlkezeléshez,
háttérszinkronizációhoz és platformazonosításhoz.

MockDataGenerator.kt: Szintetikus szenzoradatok generálása hardver nélküli teszteléshez.

2. Android-specifikus megvalósítás (androidMain)

MainActivity.kt & AppContextHolder.kt: Android belépési pont és szálbiztos (AtomicReference) alkalmazáskontextus-tároló.

SensoriaBluetoothManager.kt: Natív Android BLE szkennelés (Service UUID szűréssel), párhuzamos GATT-kapcsolat a bal (
Sensoria-C1-D348) és jobb (Sensoria-C1-9C1D) zoknihoz, CCCD értesítések engedélyezése és host_time_ms időbélyegzés.

CameraPreview.android.kt: CameraX (ProcessCameraProvider, Preview, VideoCapture<Recorder>) alapú előlapi előnézet és HD
MP4 rögzítés.

VideoPlayer.android.kt & VideoPicker.android.kt: Media3 ExoPlayer alapú végtelenített videólejátszó és
ActivityResultContracts.GetContent videóválasztó.

FileStorage.android.kt: Lokális fájlkezelés a context.filesDir könyvtárban, a feltöltött mérési JSON-ök _synced.json
utótaggal való megjelölésével.

SyncManager.android.kt & SessionSyncWorker.kt: WorkManager (CoroutineWorker) alapú, aktív internetkapcsolathoz (
NetworkType.CONNECTED) kötött háttérszinkronizáció.

Platform.android.kt: Az aktuális Android SDK-verzió lekérdezése.

3. Adatbázis és szerveroldali kiértékelés (.sql)

A PostgreSQL séma két elkülönített rétegre épül:

Védett belső séma (zokni): Itt találhatók a fizikai táblák (athletes, exercises, sessions, video_json,
extracted_keypoints, biomechanics_results, sock_analysis_results, combined_analysis_results) bekapcsolt Row Level
Security (RLS) szabályokkal.

Publikus API réteg (public): A kliens kizárólag security_invoker='true' beállítású nézeteken (athletes, exercises,
sessions, deleted_sessions, biomechanics_results) és SECURITY DEFINER RPC-függvényeken (restore_session,
get_my_sock_analysis) keresztül kommunikál az adatbázissal. A nézeteken elhelyezett INSTEAD OF triggerek biztosítják az
írási műveleteket és a logikai törlést (soft delete).

Automatizált kiértékelő triggerek

trg_after_video_json_insert: A videóalapú pózbecslés eredményéből (video_json) kinyeri a 17+2 anatómiai kulcspontot az
extracted_keypoints táblába.

trg_calc_biomechanics: Az analysis_status = 'inserted' állapot elérésekor lefuttatja a szűrést, az anatómiai skálázást,
a de Leva-féle CoM-számítást, a poszturális mutatókat és a törzsszög-idősor mentését (biomechanics_results).

trigger_evaluate_sock_on_inserted: Videós feldolgozás után (inserted) vagy videó nélküli mérésnél (sock_only) elindítja
a talpi nyomáselemzést (evaluate_sock_biomechanics).

trigger_combined_on_video / trigger_combined_on_sock: Mindkét modalitás elkészültekor összeveti a felsőtesti és talpi
kilengéseket (combined_analysis_results).

Konfiguráció (saját környezetben való futtatáshoz)

Mivel a hitelesítő adatok ki vannak zárva a verziókövetésből, a projekt lefordításához hozz létre egy SupabaseClient.kt
fájlt az org.example.project.network csomagban az alábbi mintával:

package org.example.project.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {
private const val SUPABASE_URL = "YOUR_SUPABASE_URL"
private const val SUPABASE_KEY = "YOUR_SUPABASE_ANON_KEY"

    val supabaseClient = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
        install(Postgrest)
        install(Storage)
        install(Auth)
    }

}

Ezután futtasd le a mellékelt SQL migrációs sémát a saját Supabase/PostgreSQL példányodon, és hozd létre a
measurement-videos valamint az exercise-videos Storage bucketeket.

Technológiai stack

Mobilkliens: Kotlin Multiplatform (KMP), Jetpack Compose (Material 3), Kotlin Coroutines & Flow, kotlinx.serialization,
kotlinx.datetime

Android natív modulok: Android BLE / GATT API, CameraX, Media3 ExoPlayer, AndroidX WorkManager

Backend & Adatbázis: Supabase (supabase-kt 3.0.0, Ktor), PostgreSQL, PL/pgSQL, Row Level Security (RLS)