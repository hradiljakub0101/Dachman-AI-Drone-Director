# Android 0.15.3: mapa při přiblížení a kreslení oblastí

## Změny

- Ruční posun nebo přiblížení mapy vypne automatické centrování na dron. Tlačítko `⌖` znovu zapne sledování a vystředí aktuální polohu dronu.
- Přiblížení mapy je omezené na rozsah dostupných dlaždic satelitního podkladu, aby nad maximální úrovní nezůstaly jen značky na šedé ploše.
- Přepínač `SAT / MAP` umožňuje použít satelitní snímky Esri nebo silniční podklad OpenStreetMap, pokud dlaždice satelitního zdroje nejsou dostupné.
- Kreslení obrysu střechy a zakázané oblasti zůstává navázané na klepnutí do mapy; vykreslené body se předávají bezpečnostnímu plánu.
- Oprava Home Pointu z verze 0.15.2 zachovává potvrzený bod a výšku RTH, i když DJI odmítne samostatnou volbu Smart RTH nebo nastavení failsafe.

## Omezení ověření

Automatické kontroly zdrojového kódu a sestavení ověřují propojení mapového ovládání a aplikace. Úspěch dlaždic závisí také na síti telefonu a dostupnosti poskytovatele map. Nebyl proveden fyzický letový test.
