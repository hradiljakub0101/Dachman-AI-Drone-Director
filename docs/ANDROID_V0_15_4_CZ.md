# Android 0.15.4: předání řízení a ověřování návratu domů

## Zjištění z dodaného snímku

DJI při pokusu o návrat vrátilo chybu `Execution of this process has timed out`. Snímek byl pořízen při výšce nula a GNSS nula, proto z něj nelze zjistit, zda letový kontrolér měl v okamžiku odeslání RTH platnou lokalizaci. Chyba samotná nepotvrzuje, že Home Point nebyl uložen.

## Opravy

- Vypnutí Virtual Stick se musí dokončit a jeho stav se musí přečíst zpět z DJI před odesláním `startGoHome()`. Souběžné požadavky na vypnutí čekají na jediný výsledek.
- Před RTH se z živé telemetrie ověří, že DJI aktuálně hlásí Home Point, že souřadnice odpovídají bodu ověřenému při uložení a že dron má platnou GNSS polohu. Stejná kontrola se zopakuje po předání řízení.
- Stav uložený v aplikaci už nedoplňuje chybějící příznak Home Pointu v telemetrii DJI. UI výslovně ukazuje, kdy RTH čeká na GNSS nebo potvrzení letového kontroléru.
- Po odeslání RTH se sleduje `isGoingHome()` v živé telemetrii. Chyba či časový limit samotného příkazu není zaměněn za jistotu, že se dron nepohnul. Povel se při časovém limitu automaticky neopakuje.
- Aplikace nyní rozpozná i RTH spuštěný fyzickým tlačítkem ovladače. Dosednutí označí za potvrzený návrat k Home Pointu jen při ověřené poloze v cíli.

## Rozsah ověření

Jednotkové testy prověřují frontu předání řízení a shodu Home Pointu s telemetrií letového kontroléru. GitHub Actions sestaví DJI APK a ověří podpis a přítomnost klíče. Skutečný výsledek RTH na konkrétním Mini 2 a RC-N1 vyžaduje kontrolovaný letový test; software nesmí tvrdit, že se dron vrací, dokud to nepotvrdí telemetrie DJI.
