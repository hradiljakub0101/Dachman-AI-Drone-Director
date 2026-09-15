# Firmware DJI Mini 2 a nahrávání

## Co patří do aplikace

- Android aplikace používá oficiální DJI Mobile SDK `4.18`.
- Podle schopností připojené kamery volí `FlatCameraMode.VIDEO_NORMAL`, nebo starší
  kompatibilní cestu `CameraMode.RECORD_VIDEO`, a poté volá `startRecordVideo`.
- Skutečný stav záznamu čte z callbacku kamery, nikoli ze stavu tlačítka.
- Stav microSD karty čte přes `setStorageStateCallBack`; bez připravené karty záznam
  nespustí.

DJI dokumentace výslovně uvádí, že `setStorageLocation` není u DJI Mini 2 podporované.
Když kamera nemá volitelné interní úložiště, vrací jako místo nových médií vždy
`SDCARD`. Aplikace proto neposílá neplatný povel k přepnutí úložiště.

## Co do APK ani GitHub Actions nepatří

Firmware dronu ani RC-N1 se nevkládá do APK, repozitáře nebo sestavovacího workflow.
DJI na produktové stránce zveřejňuje poznámky k vydání, ale vlastní distribuční a
ověřovací proces aktualizace provádí DJI Fly nebo DJI Assistant 2 (Consumer Drones
Series). Vlastní nebo upravený firmware by mohl zneplatnit kompatibilitu, bezpečnostní
omezení a obnovitelnost zařízení.

## Bezpečný postup aktualizace

1. Nabij dron, RC-N1 a telefon alespoň na polovinu kapacity a vyjmi vrtule.
2. Připoj Mini 2 v oficiální aplikaci DJI Fly a přijmi pouze aktualizaci, kterou nabídne
   přímo DJI. Alternativně použij DJI Assistant 2 (Consumer Drones Series) z oficiálního
   centra DJI.
3. Během aktualizace zařízení nevypínej a neodpojuj kabel.
4. Po aktualizaci spusť nejprve DJI Fly, ověř obraz, SD kartu a ruční záznam.
5. DJI Fly úplně ukonči, připoj ovladač k Dachman AI a proveď pozemní akceptační test
   `REC -> STOP -> kontrola nového MP4 na microSD`.

Oficiální rozcestník: <https://www.dji.com/downloads/products/mini-2>

Oficiální DJI Assistant 2: <https://www.dji.com/downloads/softwares/dji-assistant-2-consumer-drones-series>
