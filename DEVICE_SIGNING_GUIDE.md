# Podepisování a registrace mobilních aplikací

## Tři různé věci

1. DJI App Key registruje aplikaci u DJI. iOS a Android potřebují samostatnou registraci.
2. Certifikát Apple nebo Android keystore podepisuje instalační soubor a jeho aktualizace.
3. Systémová biometrie / kód zařízení ověřuje držitele zařízení před schválením manévru.
   Nejde o ověření jména pilota, licence, DJI účtu ani firemní role.

## iPhone / iOS

- Bundle ID: `cz.dachman.drone.director`.
- DJI MSDK 4.16.2 čte `DJISDKAppKey` z **výsledného** Info.plist.
- Zdrojový Info.plist ponechte se zástupnou hodnotou. Nesdílejte skutečný klíč v Gitu.
- Exportujte `DJI_APP_KEY` do prostředí Xcode / xcodebuild. Při spuštění Xcode z Finderu
  prostředí terminálu není automaticky dostupné: pro reprodukovatelné sestavení použijte skript.
- XcodeGen přidává poslední build fázi `scripts/inject_dji_key.py`. Ta závisí na zpracovaném
  plist, vloží klíč do něj a skončí před podpisem aplikace. Zdrojový plist neupravuje.
- Podepsané device sestavení a každé Release sestavení bez skutečného klíče skončí chybou.
  Nepodepsaný Debug pro CI může mít placeholder; aplikace pak registraci odmítne.
- Runtime setter byl odstraněn: dříve hlásil klíč načtený, který SDK ve skutečnosti nepoužilo.
- Apple certifikát a profil musí odpovídat Team ID, Bundle ID a distribuční metodě.

Na Macu s Xcode a již přidaným Apple účtem/certifikátem:

```sh
export APPLE_TEAM_ID="YOUR_TEAM_ID"
# DJI_APP_KEY nastavte bezpečně v prostředí; nevkládejte jej do sdíleného příkazu.
bash scripts/archive_ios.sh
```

Archiv je v `iOSApp/build/DachmanDroneDirector.xcarchive`. Export IPA provádějte přes
Xcode Organizer podle zamýšleného použití (registrované zařízení, TestFlight, App Store).
Skript nevytváří Apple účet, nekupuje členství a neslibuje dostupnost distribučního profilu.
Pro lokální zařízení nastavte správný Signing Team; zařízení může být nutné zaregistrovat.

## Android / APK

- Projekt: `androidApp`, Java 17, Gradle 8.11.1, Android SDK 35, minimum Android 11.
- `demo`: offline kontrola UI pro CI, bez DJI SDK, balíček `cz.dachman.drone.director.demo`.
- `dji`: DJI MSDK 4.18, balíček `cz.dachman.drone.director`.
- Pro variantu dji vytvořte **Android** DJI App Key pro uvedený balíček.
- Proměnná `DJI_ANDROID_APP_KEY` se během sestavení vloží do manifest metadata
  `com.dji.sdk.API_KEY`, které DJI SDK skutečně čte. Žádný runtime falešný override.

```sh
cd androidApp
gradle testDemoDebugUnitTest assembleDemoDebug assembleDjiDebug
```

Debug APK jsou v `app/build/outputs/apk/<varianta>/debug/`. Gradle je podepíše vývojovým
klíčem. Ten není distribuční identita: CI debug klíč se může mezi běhy měnit.
Bez DJI klíče lze DJI variantu sestavit pro CI, ale registrace dronu bude zablokována.

Pro ruční CI build s tvým klíčem přidej v GitHub Settings > Secrets and variables > Actions
repository secret `DJI_ANDROID_APP_KEY` a spusť `Android APK and approval tests` přes Run
workflow se zapnutým `use_dji_key`. Po merge do chráněné větve `main` se klíčový debug APK
sestaví automaticky; pull-request kontroly zůstávají bez klíče.

Pro Release připravte vlastní dlouhodobý keystore v Android Studiu (Generate Signed
Bundle / APK). Pokud již aplikaci distribuujete, použijte její existující podpis.
Keystore zálohujte mimo repozitář. Nastavte pouze v bezpečném prostředí:

- `ANDROID_KEYSTORE_PATH` – absolutní cesta k souboru;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`;
- `DJI_ANDROID_APP_KEY` pro dji Release.

Poté `gradle assembleDjiRelease` nebo `gradle assembleDemoRelease`.
Release bez kompletního podpisu skončí chybou; dji Release navíc vyžaduje DJI klíč.
Aplikace sama nevytváří certifikáty během registrace uživatele.

## CI a ochrana klíčů

Pull requesty spouštějí jen testy a Debug bez provozních tajemství. iOS CI je nepodepsaný
compatibility build, nikoli IPA pro instalaci. Android CI zveřejní debug APK jako artifact.
Současné workflow záměrně nemá Release s přístupem ke klíčům z nedůvěryhodných PR.
Před automatickým distribučním sestavením přidejte chráněné release prostředí a jeho secrets.

DJI App Key bude součástí IPA/APK a není ekvivalentem serverového tajemství.
Soukromý podepisovací klíč se do aplikace nebalí. Nezapínejte verbose logování tajemství.
`.gitignore` vylučuje env soubory, keystory, certifikáty, profily a build výstupy.

## Ověření na zařízení, které stále zbývá

- Biometrie/kód: úspěch, odmítnutí, zrušení, chybějící zámek, změna manévru, HOLD/ABORT a pozadí.
- DJI: platný/neplatný klíč, prvotní internetová registrace, RC-N1, USB a skutečný Mini 2.
- MSDK 4 Android obsahuje starší nativní knihovny: zvlášť ověřte 16KB stránky a Android 15+.
  Samotný targetSdk tento problém neopraví. Projekt není prohlášen za připravený pro Google Play.
- Android: proveď celý postup v `docs/ANDROID_FLIGHT_ACCEPTANCE_CZ.md`; softwarový build
  nenahrazuje ověření skutečného Mini 2, RC-N1 a telefonu.

Zdroje:
- https://github.com/dji-sdk/Mobile-SDK-iOS (DJISDKAppKey v plist)
- https://github.com/dji-sdk/Mobile-SDK-Android (MSDK 4.18)
- https://github.com/dji-sdk/Mobile-SDK-Android/issues/1343 (16KB compatibility)
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/identity/sign-in/biometric-auth
- https://developer.apple.com/documentation/localauthentication
