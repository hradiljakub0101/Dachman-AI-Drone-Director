# Android 0.15.1: volitelný let bez Home Pointu

## Změna ovládání

- Karta návratového bodu obsahuje volbu **HOME POINT NEDOSTUPNÝ – POVOLIT AUTONOMNÍ VZLET A POHYB**.
- Po zapnutí této volby lze spustit autonomní vzlet a všechny režimy Virtual Stick i bez Home Pointu a bez souřadnic dronu: FOLLOW, GROUP, ORBIT, mapovací oblet, vzdálený odjezd, stoupavý průlet a ROPE.
- Pokud pilot volbu nezapne, zachovává se původní podmínka potvrzeného DJI Home Pointu a polohy dronu.
- RTH je dostupné jen s Home Pointem, který DJI skutečně přijalo a aplikace zpětně ověřila.
- Úplná mise spuštěná bez Home Pointu po posledním záběru přejde do visu a předá řízení pilotovi; nepokusí se spustit neplatné RTH.

## Ověření

- Izolovaný test letové smyčky spouští každý autonomní režim bez Home Pointu a bez GPS po výslovné volbě pilota.
- Samostatný test potvrzuje, že bez této volby zůstane stejný stav zablokovaný.
- GitHub Actions spouští jednotkové testy, softwarový tester letové smyčky a sestavení demo i DJI APK.

Přijetí příkazu DJI SDK neprokazuje skutečný pohyb konkrétního dronu. První ověření této volby proveď v otevřeném prostoru, s pilotem připraveným okamžitě převzít řízení RC-N1.
