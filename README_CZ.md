# Dachman AI Drone Director

## Android – kompletní supervised-flight aplikace

Android verze `0.8.0` pro DJI Mini 2 obsahuje živý obraz, telemetrii, offline detekci
osob EfficientDet Lite, označení Worker 1 a Worker 2, animovaný náhled trajektorie,
jednorázové systémové ověření a skutečné řízení přes DJI Mobile SDK `4.18`.

Každý schválený filmový režim automaticky zapíná také kameramana AI. Podle pohybu
vybraného Worker 1 nebo společného rámečku Worker 1 + Worker 2 predikuje krátký pohyb,
udržuje kompoziční prostor, plynule naklání gimbal nahoru/dolů a nastavuje podporovaný
digitální zoom Mini 2 v konzervativním rozsahu `1,0×–2,0×`. Horizontální centrování
zajišťuje omezený yaw dronu, protože gimbal Mini 2 sleduje směr letadla.

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

Před vzletem je povinné tlačítko `ULOŽIT BOD VZLETU`. Aplikace zapíše Home Point
na aktuální polohu dronu, načte jej zpět, ověří odchylku, nastaví RTH výšku,
zapne Smart RTH a nastaví ztrátu spojení na `GO_HOME`. Běžné ukončení letu používá
`NÁVRAT A PŘISTÁNÍ`; přímé přistání na aktuálním místě je oddělená nouzová akce
aktivovaná dlouhým podržením a následným bezpečnostním potvrzením.

Tok řízení je:

`DJI video -> offline AI -> potvrzený pracovník -> letový + kamerový planner -> SafetySupervisor -> animovaný náhled -> jednorázové ověření -> Virtual Stick + gimbal + digitální zoom`

Tlačítko `REC` ovládá přímo kameru dronu. Aplikace sleduje skutečný stav nahrávání
z DJI `SystemState`, před spuštěním ověří vloženou, inicializovanou, naformátovanou,
zapisovatelnou a nezaplněnou microSD kartu a její stav ukazuje v horní liště. Mini 2
nemá volitelné interní úložiště pro média, proto DJI ukládá nově pořízené video přímo
na microSD kartu v dronu. Pro firmware podporující Flat Camera Mode aplikace používá
`VIDEO_NORMAL`; starší `RECORD_VIDEO` zůstává pouze jako kompatibilní záloha.

Aplikace používá vlastní černobílé logo jako standardní, kulatou, adaptivní i
monochromatickou ikonu Androidu. Zdrojový podklad a čistý master jsou uložené
v repozitáři, takže následující buildy zachovají stejnou identitu aplikace.

Pohyb kteréhokoli fyzického kniplu nebo kolečka gimbalu RC-N1 má přednost a vypne
Virtual Stick i automatickou kameru nebo zruší probíhající automatickou akci. AI se po
zásahu sama znovu nezapne; vyžaduje ruční přípravu a nové potvrzení v APK. Mini 2 nemá
všesměrové vyhýbání překážkám, proto aplikace nenahrazuje pilota ani kontrolu volné trasy.

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
