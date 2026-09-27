# Android 0.15.12 – potvrzené REC a telefonní H.264 záloha

## Chování tlačítka REC

1. Aplikace ověří připojení kamery a aktuální stav microSD.
2. Načte aktuální `FlatCameraMode` nebo `CameraMode`.
3. Nastaví `VIDEO_NORMAL` / `RECORD_VIDEO`, pokud je potřeba.
4. Režim znovu načte a bez potvrzení spuštění záznamu nepokračuje.
5. Počká 350 ms na ustálení kamery a zavolá `startRecordVideo`.
6. Tlačítko zobrazuje `REC…`, dokud DJI ve `SystemState` nepotvrdí `isRecording=true`.
7. Pokud se to nepotvrdí, aplikace zobrazí `REC DJI FAILED` s režimem, chybou startu, stavem nahrávání a stavem microSD.

Ťuknutím na stav SD se otevře diagnostika kamery, potvrzení REC, dostupnosti a stavu SD, hlášeného volného místa a telefonní zálohy.

## Záložní záznam telefonu

Během stisku REC aplikace současně ukládá přijatý DJI VideoFeeder stream do:

`Downloads/Dachman Drone Director/Dachman_Phone_Backup_<čas>.h264`

Při chybě nebo nepřipravené microSD se spustí telefonní záznam, pokud je živý přenos dostupný. Při selhání SD záznamu za letu telefonní záloha pokračuje. Při plně funkčním SD záznamu tlačítko STOP uzavře oba záznamy.

**Telefonní soubor je surový H.264 stream, ne MP4 a ne originální plné rozlišení z kamery.** Nemá zvuk ani zaručené časové značky. Pro první kontrolu ho otevři přehrávačem, který podporuje H.264 elementary stream (například VLC). Jeho úplnou přehratelnost a chování při výpadku přenosu je nutné ověřit na konkrétním telefonu a RC.

## Test před letem

### Test na zemi – karta vložená

- Připoj telefon k ovladači, zapni dron a počkej na živý obraz.
- V diagnostice ověř připojenou kameru, připravenou SD a volné místo.
- Stiskni REC. Tlačítko musí nejdřív ukázat `REC…`; teprve po potvrzení DJI se změní na STOP.
- Nech záznam krátce běžet a stiskni STOP.
- Ověř původní soubor na microSD a záložní soubor v uvedené složce Downloads.
- Přehraj oba soubory.

### Test na zemi – bez dostupné SD

- Použij pouze krátký test na zemi.
- Ověř, že REC spustí `PHONE BACKUP H.264`, tlačítko ukazuje telefonní záznam a vznikne soubor v Downloads.
- Ověř soubor v kompatibilním přehrávači.

### Test při skutečném letu

Samostatně ověř, že aktivní SD REC přežije převzetí řízení kniply a že při případném přerušení SD záznamu zůstane telefonní záloha aktivní. Automatické testy ani sestavení APK tento fyzický test nenahrazují.
