# Akceptační test Android aplikace s DJI Mini 2

Tento postup odděluje ověřený software od ověření skutečného letadla. Zelený CI build
prokazuje překlad, unit testy, zabalení modelu a podpis APK. Teprve následující zkouška
ověří konkrétní telefon, kabel, RC-N1, firmware, DJI účet a Mini 2.

## Než se nasadí vrtule

- Nainstaluj pouze DJI variantu `cz.dachman.drone.director` s odpovídajícím DJI App Key.
- Připoj zapnutý RC-N1 a Mini 2, udělej oprávnění a ověř stav `SDK OK / DRON OK`.
- Ověř živý obraz, změnu baterie, GNSS, signálu a letového režimu.
- Ověř spuštění a zastavení záznamu a jednu fotografii na SD kartu.
- V obrazu označ Worker 1 a Worker 2 a ověř, že rámečky drží zvolenou osobu při pohybu.
- Zkus schválení, odmítnutí, zrušení a zamknutí telefonu. Bez úspěšné biometrie nebo
  kódu se nesmí spustit žádná akce.

## První let – otevřený prostor

Podmínky: zkušený pilot, přímý dohled, bez lidí v trase, bez vedení, stromů a budov,
slabý vítr, plně nabitý RC a minimálně dvacet pět procent baterie dronu. Výchozí profil
je `PŘESNÝ` a výškový strop osm metrů.

1. Potvrď volný prostor, zvol `VZLET` a projdi systémovým ověřením.
2. Očekávej automatický vzlet do visu DJI a stav bez aktivního Virtual Stick.
3. Označ Worker 1, zvol `STATICKÉ SLEDOVÁNÍ`, schval a ověř jen yaw/gimbal.
4. Aktivuj `FOLLOW` a lehce vychyl kterýkoli fyzický knipl. Do jedné periody řízení,
   tedy přibližně sta milisekund, se musí zobrazit `PILOT OVERRIDE` a Virtual Stick vypnout.
5. Ověř ztrátu cíle: přibližně po devíti stech milisekundách musí přijít HOLD a po
   zhruba dvou celých dvou desetinách sekundy úplné ukončení režimu.
6. Postupně otestuj statické sledování, Follow, Duo Follow, oblet vlevo, oblet vpravo,
   odjezd, stoupavé odhalení a režim lana. Vždy začni profilem `PŘESNÝ`.
7. Zvol `PŘISTÁNÍ`, potvrď kontrolu prostoru a ověř. Aplikace musí zahájit sestup a
   při požadavku DJI potvrdit závěrečné dosednutí pod třicet centimetrů.
8. V dalším letu ověř `NÁVRAT DOMŮ` až po kontrole domovského bodu a bezpečné RTH výšky.
   Během celé trasy drž přímý dohled a připravený ovladač.

## Kritéria PASS

- Žádná akce se nespustí bez čerstvého jednorázového ověření.
- Nízká baterie, failsafe, probíhající RTH, silný vítr, slabý signál nebo překročený
  zvolený strop zablokují filmový režim.
- Pohyb kniplu ukončí filmový režim nebo zruší právě probíhající automatickou akci.
- Po ztrátě cíle aplikace neposílá nenulové pohybové povely.
- HOLD, ABORT, odchod aplikace do pozadí a odpojení zastaví Virtual Stick.
- Vzlet, přistání a RTH dokončí DJI letový kontrolér a stav v aplikaci odpovídá telemetrii.

Jakákoli odchylka znamená `FAIL`: další letové režimy se netestují, problém se zaznamená
a aplikace se vrací do vývoje.
