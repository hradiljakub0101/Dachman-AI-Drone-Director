# Android 0.15.8 – kontrolovaný test přiblížení k Home

## Co režimy opravdu provádějí

- **NÁVRAT A PŘISTÁNÍ:** ověřené DJI RTH a přistání po potvrzení letu letovým kontrolérem. U Mini 2 spuštění uvnitř dvaceti metrů nevede k letu na Home; použij tento režim pouze za hranicí dvaceti metrů. Při ztrátě spojení a kritické baterii rozhoduje firmware DJI; zkušební přiblížení jej nenahrazuje.
- **TEST: PŘIBLÍŽIT K HOME (BEZ PŘISTÁNÍ):** samostatný pilotem potvrzený let přes DJI Virtual Stick od vzdálenosti nejvýše 19,5 m k uloženému pevnému Home Pointu. Rychlost nejvýše 0,35 m/s, přibližně v osmi metrech od Home uvolní řízení a zůstane ve visu. Nepoužívá GPS telefonu, nepřistává a není DJI RTH. Při příkazu „na mě“ se rozumí jen místo skutečně uloženého Home, nikoli pohybující se osoba.

## Podmínky zapnutí a zastavení

Vyžaduje uložený a ověřený DJI Home Point; rozdíl mezi ověřeným Home a bodem hlášeným letovým kontrolérem do 2,5 m; alespoň dvanáct satelitů; baterii nejméně 35 %; rádiový signál nejméně 40 %; rychlost nejvýše 1,5 m/s; čerstvou telemetrii, platnou GPS a potvrzení pilotem. Nelze zapnout s volbou letu bez Home. Trajektorie respektuje zakreslené hranice a zakázané zóny v místě dronu a dva metry před ním. Deset sekund bez ověřitelného pokroku, změna Home, chyba SDK, ztráta polohy a zásah kniplem přeruší pohyb a uvolní Virtual Stick. Limit manévru je 45 sekund. Zastavení v osmi metrech není zaručená přesnost GPS.

Mini 2 nemá ověřené vyhýbání překážkám pro tento vlastní algoritmus. Funkci lze zkoušet pouze venku nad volnou přímou trasou, mimo osoby a stavby, s přímým dohledem a s pilotem připraveným převzít RC-N1. Před reálným letem zkontrolovat nastavení DJI failsafe a skutečné souřadnice Home. Zásah kniplem vyžaduje následné ruční znovupovolení AI. Po dosažení zóny visu přistává pilot.

## Ověřování

Izolovaná fyzikální simulace provádí tři samostatná přiblížení z deseti, čtrnácti a devatenácti metrů při různém natočení dronu a kontroluje signál DJI Virtual Stick, zastavení před Home, vypnutí virtuálního řízení a nulový příkaz k přistání. Další testy zastavují postup při změně Home, ztrátě GPS, převzetí pilotem a při průniku zakreslenou zakázanou zónou; předletové kontroly odmítají nedostatečnou baterii, GNSS, signál a vzdálenost. Simulace nepokrývá skutečné chyby GNSS, vítr, překážky, radiofrekvenční rušení ani chování firmwaru Mini 2. Úspěch testů a sestavení APK znamená způsobilost k řízenému terénnímu ověření, ne prokázání bezpečného autonomního letu.

**Fyzický postup:** nejprve ověřit bezmotorové předletové blokace a kniplový override; potom jednotlivé lety na volné ploše z deseti, čtrnácti a devatenácti metrů s pilotem připraveným k převzetí a s přistáním provedeným ručně. Nad dvaceti metry ověřit zvlášť nativní DJI RTH. Zaznamenat letové logy, odchylku od Home, dobu do zastavení a výsledek přepnutí řízení. Nenasazovat u pracovníků ani budov, dokud nejsou terénní výsledky vyhodnoceny.
