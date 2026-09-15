# Akceptační test Android aplikace s DJI Mini 2

Tento postup odděluje ověřený software od ověření skutečného letadla. Zelený CI build
prokazuje překlad, unit testy, zabalení modelu a podpis APK. Teprve následující zkouška
ověří konkrétní telefon, kabel, RC-N1, firmware, DJI účet a Mini 2.

## Než se nasadí vrtule

- Nainstaluj pouze DJI variantu `cz.dachman.drone.director` s odpovídajícím DJI App Key.
- Připoj zapnutý RC-N1 a Mini 2, udělej oprávnění a ověř stav `SDK OK / DRON OK`.
- Ověř živý obraz, změnu baterie, GNSS, signálu a letového režimu.
- V horní liště musí být `SD OK` nebo `SD OK?`. Vyjmi kartu a ověř, že se zobrazí
  `SD CHYBÍ`, tlačítko `REC` se zablokuje a aplikace nahlásí konkrétní důvod.
- Vlož zapisovatelnou kartu s volným místem. Stiskni `REC`, ověř změnu na `STOP`,
  červený indikátor kamery Mini 2 a rostoucí čas nahrávání. Stiskni `STOP`, vypni dron,
  vyjmi kartu a ověř nový soubor MP4 v adresáři `DCIM/100MEDIA` (nebo v aktuální
  složce `DCIM`, kterou vytvořil firmware). Stejně ověř jednu fotografii.
- V obrazu označ Worker 1 a Worker 2 a ověř, že rámečky drží zvolenou osobu při pohybu.
- Ověř, že stav `KAMERA AI` zůstává vypnutý v MANUAL a přejde do `PŘIPRAVENA` až po
  potvrzení Worker 1. Digitální zoom Mini 2 je záměrně omezen na `1,0×–2,0×`.
- Zkus schválení, odmítnutí, zrušení a zamknutí telefonu. Bez úspěšné biometrie nebo
  kódu se nesmí spustit žádná akce.

## První let – otevřený prostor

Podmínky: zkušený pilot, přímý dohled, bez lidí v trase, bez vedení, stromů a budov,
slabý vítr, plně nabitý RC a minimálně dvacet pět procent baterie dronu. Výchozí profil
je `PŘESNÝ` a výškový strop osm metrů.

1. Polož dron na místo návratu, počkej alespoň na osm satelitů, nastav bezpečnou RTH
   výšku a zvol `ULOŽIT BOD VZLETU`.
2. Ověř zelený stav Home Pointu, zpětně načtené souřadnice, vzdálenost, Smart RTH
   a failsafe `GO_HOME`. Bez tohoto stavu musí být vzlet zablokovaný.
3. Potvrď volný prostor, zvol `VZLET` a projdi systémovým ověřením.
4. Očekávej automatický vzlet do visu DJI a stav bez aktivního Virtual Stick.
5. Označ Worker 1, zvol `STATICKÉ SLEDOVÁNÍ`, schval a ověř yaw/gimbal. Pracovník se
   pomalu přesune nahoru a dolů v obrazu: gimbal se musí plynule vrátit ke kompozici,
   v mrtvé zóně zastavit a nesmí kmitat.
6. Nech pracovníka bezpečně změnit vzdálenost. Ověř postupné kroky digitálního zoomu,
   oddálení u okraje snímku a zobrazenou hodnotu `ZOOM`. Pokud daný video režim povel
   odmítne, aplikace to musí oznámit a ponechat automatický gimbal aktivní.
7. Aktivuj `FOLLOW` a lehce vychyl kterýkoli fyzický knipl. Do jedné periody řízení,
   tedy přibližně sta milisekund, se musí zobrazit `PILOT OVERRIDE` a Virtual Stick vypnout.
8. Režim znovu ručně připrav, spusť jej a otoč kolečkem gimbalu RC-N1. Očekávej stejné
   trvalé převzetí pilotem; AI kamera se sama nesmí znovu zapnout.
9. Ověř ztrátu cíle: přibližně po devíti stech milisekundách musí přijít HOLD a po
   zhruba dvou celých dvou desetinách sekundy úplné ukončení režimu.
10. Postupně otestuj statické sledování, Follow, Duo Follow, oblet vlevo, oblet vpravo,
   odjezd, stoupavé odhalení a režim lana. Vždy začni profilem `PŘESNÝ`.
11. Zvol `NÁVRAT A PŘISTÁNÍ`, potvrď trasu a ověř. Dron musí použít uložený Home Point,
   nastavenou RTH výšku a pod třiceti centimetry potvrdit dosednutí.
12. Nouzové tlačítko pouze krátce stiskni: nesmí se nic spustit. Potom jej dlouze podrž,
   proveď druhé potvrzení a pouze v bezpečném prostoru ověř přistání na místě.
13. Při řízeném testu bateriové pojistky ověř, že aplikace odešle RTH pouze jednou,
   vypne Virtual Stick a zobrazí stav, že řízení převzal letový kontrolér DJI.

## Kritéria PASS

- Žádná akce se nespustí bez čerstvého jednorázového ověření.
- Nízká baterie, failsafe, probíhající RTH, silný vítr, slabý signál nebo překročený
  zvolený strop zablokují filmový režim.
- Pohyb kniplu ukončí filmový režim nebo zruší právě probíhající automatickou akci.
- Kolečko gimbalu ukončí kameru AI i filmový režim a vyžádá ruční opětovnou přípravu.
- Automatický gimbal nepřekročí profilový limit a zoom nepřekročí `2,0×`; u okraje
  záběru musí algoritmus přednostně oddálit.
- Po ztrátě cíle aplikace neposílá nenulové pohybové povely.
- HOLD, ABORT, odchod aplikace do pozadí a odpojení zastaví Virtual Stick.
- Vzlet, přistání a RTH dokončí DJI letový kontrolér a stav v aplikaci odpovídá telemetrii.
- `REC` se nesmí aktivovat bez připravené microSD karty. Zelený stav tlačítka není
  důkazem sám o sobě: po zastavení musí na kartě existovat přehratelný nový soubor MP4.

Jakákoli odchylka znamená `FAIL`: další letové režimy se netestují, problém se zaznamená
a aplikace se vrací do vývoje.
