# Dachman AI Drone Director

## Android – kompletní supervised-flight aplikace

Android verze `0.3.0` pro DJI Mini 2 obsahuje živý obraz, telemetrii, offline detekci
osob EfficientDet Lite, označení Worker 1 a Worker 2, animovaný náhled trajektorie,
jednorázové systémové ověření a skutečné řízení přes DJI Mobile SDK `4.18`.

K dispozici jsou režimy:

- statické sledování;
- Follow a Duo Follow;
- oblet vlevo a vpravo;
- odjezd;
- stoupavé odhalení;
- režim lana;
- HOLD a ABORT.

Rychlost lze volit mezi profily `PŘESNÝ`, `STANDARDNÍ` a `FILMOVÝ`. Výškový strop je
volitelný po jednom metru od tří do sto dvaceti metrů; je to bezpečnostní omezení,
nikoli příkaz automaticky do zvolené výšky vystoupat.

Po samostatném potvrzení a biometrickém ověření umí aplikace také autonomní vzlet,
autonomní přistání včetně potvrzení závěrečného dosednutí a DJI Return-to-Home.
Vzlet, přistání ani RTH nejsou skrytou součástí filmového režimu a každý vyžaduje
vlastní nové schválení.

Tok řízení je:

`DJI video -> offline AI -> potvrzený pracovník -> omezený planner -> SafetySupervisor -> animovaný náhled -> jednorázové ověření -> Virtual Stick`

Pohyb kteréhokoli fyzického kniplu RC-N1 má přednost a vypne Virtual Stick nebo zruší
probíhající automatickou akci. Mini 2 nemá všesměrové vyhýbání překážkám, proto aplikace
nenahrazuje pilota ani kontrolu volné trasy.

Sestavení a podpis jsou popsány v [DEVICE_SIGNING_GUIDE.md](DEVICE_SIGNING_GUIDE.md).
Povinný test konkrétního dronu je v
[docs/ANDROID_FLIGHT_ACCEPTANCE_CZ.md](docs/ANDROID_FLIGHT_ACCEPTANCE_CZ.md).

## iOS – původní supervised-flight větev

První iPhone větev pro DJI Mini 2. Cíl aplikace:

`Worker 1 / Worker 2 -> FOLLOW -> TRAJECTORY REVIEW -> ORBIT -> ROPE MODE -> PULL AWAY -> HOLD / ABORT`

## Co už je v projektu

- čisté Swift flight-core jádro s unit testy,
- multi-worker model pro Worker 1 až Worker 4,
- Primary + Secondary a Duo Follow,
- Rope Mode s přísnějším limitem pohybu,
- český parser hlasových povelů,
- SwiftUI prototyp obrazovky,
- Apple Speech hlasový vstup,
- Vision detektor lidí pro budoucí živé DJI snímky,
- Objective-C DJI Mini 2 bridge pro MSDK 4.16.2,
- Virtual Stick bridge,
- hardwarový pilot override: pohyb fyzického kniplu deaktivuje Virtual Stick,
- XcodeGen konfigurace,
- CocoaPods konfigurace,
- GitHub Actions kompatibilitní build pro macOS 26 / Xcode 26.6.

## Kritická bezpečnostní zásada iOS větve

AI příkaz nikdy nejde přímo do dronu. Tok je:

`Voice/Vision -> Cinematic Planner -> Safety Supervisor -> Trajectory Review -> Pilot approval -> DJI bridge`

Fyzický pohyb kniplu má vyvolat vypnutí Virtual Stick. Tuto funkci je nutné ověřit na skutečném Mini 2 / RC-N1 v testovacím prostoru před jakýmkoli použitím u lidí.

## První spuštění na Macu

1. Nainstaluj Xcode 26 nebo novější.
2. Přihlas Apple ID do Xcode.
3. V DJI Developer portálu vytvoř iOS App Key pro Bundle ID `cz.dachman.drone.director`.
4. Nastav `DJI_APP_KEY` v prostředí sestavení podle `DEVICE_SIGNING_GUIDE.md`; zdrojový plist neupravuj.
5. V kořeni projektu spusť `./scripts/bootstrap_macos.sh`.
6. Otevři `iOSApp/DachmanDroneDirector.xcworkspace`.
7. Vyber svůj iPhone jako zařízení a nastav Signing Team.

## První testy

Pořadí zůstává záměrně konzervativní:

- Swift core test bez dronu,
- iPhone UI v mock režimu,
- DJI registrace,
- telemetrie a připojení RC,
- Virtual Stick bez vrtulí / bezpečný bench test podle možností,
- otevřený venkovní prostor bez lidí,
- pilot override,
- Follow na statický neživý cíl,
- Worker tracking na člověku bez autonomního pohybu,
- teprve potom omezený supervised flight.

## DJI / iOS poznámky

Projekt používá `DJI-SDK-iOS 4.16.2` přes CocoaPods. DJI pro USB/MFi připojení vyžaduje external accessory protokoly `com.dji.video`, `com.dji.protocol`, `com.dji.common`. DJI dokumentace pro MSDK 4 také uvádí ATS `NSAllowsArbitraryLoads`; pro produkci jej později zúžíme, pokud kompatibilita dovolí.

Protože DJI iOS MSDK 4.16.2 je stará binární knihovna, první důležitý milník je compatibility build s Xcode 26. Projekt obsahuje GitHub Actions job právě pro tento účel.

Omezení identity držitele zařízení jsou popsána v
[docs/AUTHENTICATION_CZ.md](docs/AUTHENTICATION_CZ.md).
