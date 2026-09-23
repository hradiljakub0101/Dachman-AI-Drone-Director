# Android 0.15.5: ověřené předání řízení před RTH

Oprava RTH používá živé údaje z DJI FlightControlleru. Při stisku `NÁVRAT A PŘISTÁNÍ` aplikace ověří aktuální Home Point, jeho shodu s dříve uloženým bodem a živou GNSS polohu. Potom ověří přímo v DJI, zda je aktivní Virtual Stick; případné vypnutí dokončí a zpětně načte jeho stav ještě před odesláním `startGoHome()`.

Příkaz RTH se neoznačí za úspěšný podle samotné odpovědi SDK. Aplikace čeká, až FlightController nahlásí `isGoingHome()`. Při časovém limitu zobrazí stav GNSS, Home Pointu a RTH a neopakuje příkaz naslepo. Sleduje také návrat vyvolaný fyzickým tlačítkem RC-N1. Nízká baterie a Smart RTH už nespustí souběžný druhý požadavek při právě probíhajícím návratu.

Automatické testy zahrnují souběh vypnutí AI a RTH a neshodu uloženého bodu s Home Pointem v letovém kontroléru. GitHub Actions ověřuje sestavení, testy, podpis APK a vložení DJI klíče. Skutečný návrat konkrétního dronu lze potvrdit pouze kontrolovaným letovým testem v otevřeném prostoru s pilotem u RC-N1.
