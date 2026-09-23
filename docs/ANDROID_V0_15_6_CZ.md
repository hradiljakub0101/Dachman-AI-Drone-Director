# Android 0.15.6: RTH Mini 2 při letu blízko Home Pointu

- Podle dokumentace DJI Mini 2 do 20 m od Home Pointu při RTH zůstává viset a neletí k bodu. Aplikace proto před příkazem z blízkosti zobrazí skutečnou vzdálenost a doporučí řízené přistání pilotem; ověření před opakováním příkazu se provede i po předání Virtual Stick.
- Úspěšný zápis Home Pointu a RTH výšky již netvrdí, že návrat z aplikace nebo fyzickým tlačítkem byl letově ověřen.
- Chyba při nastavování volitelného Smart RTH sama neurčuje verzi firmwaru ani dostupnost základního RTH. Aplikace ji zobrazí bez nepodloženého závěru o firmwaru.
- Při chybě automatického vzletu aplikace vypíše stav motorů, letu, GNSS a režim předaný letovým kontrolérem DJI. Oprávnění ani zápis Home Pointu nezaručují povolení vzletu.
- Při čekání na novou telemetrii po spojení zobrazuje GNSS, výšku a rychlost pomlčkou místo smyšlených nul.

## Stav ověření

Softwarové integrační kontroly a sestavení v GitHub Actions ověřují napojení APK na SDK. Neověřují skutečné letové chování konkrétního dronu ani funkci tlačítka RC-N1. Pro potvrzení návratu musí pilot provést kontrolovaný test venku, v bezpečné vzdálenosti přes 20 m od Home Pointu, se záznamem telemetrie a s možností převzetí řízení. RTH ani test vzletu se nemají zkoušet uvnitř místnosti.

Firmware dronu se nevkládá do APK. Jeho skutečná verze se kontroluje a případně aktualizuje oficiální aplikací DJI Fly nebo DJI Assistant 2 podle pokynů DJI.
