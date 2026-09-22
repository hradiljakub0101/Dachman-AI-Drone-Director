# Android 0.15.0: sledovani a rizeni letu

## Zjisteni z videi 5809 a 5808

- Viditelna telemetrie uvadi GNSS 0, vysku priblizne 0 az 0.2 m a stav
  MANUAL nebo AI PRIPRAVENA. Samotny ramecek nespousti Virtual Stick.
- Baterie klesa z 27 na 23 procent v prvnim videu a ve druhem ukazuje
  18 procent. Aplikace neaktivuje AI pod bezpecnostnim limitem.
- Telefon ziskal polohu, ale DJI odmitlo zapis: Updating home point failed.
  To neni chyba opravneni Androidu. Bod nebyl kontrolerem prijat.
- Ramecek zustava viditelny i bez aktualne detekovane osoby. Nova verze
  stary ramecek oznaci ZTRACEN; nepouzije ho k pokracovani letu bez omezeni.

## Opravy

- Opraven prevod os: v DJI MSDK 4 BODY/VELOCITY je SDK pitch doprava
  a SDK roll dopredu. Interni pojmenovani pitch/roll bylo opacne.
- Pilot voli Worker 1, Worker 2 nebo Worker 1 + 2 nezavisle na letovem modu.
  Nezvoleny nebo chybejici druhy pracovnik neblokuje sledovani prvniho.
  Pri volbe obou se pri ztrate jednoho automaticky nemeni identita cile.
- Odstraneno automaticke prepnuti vyberu na Worker 2. Po vyberu se editace
  ramecku ukonci; zmena identity behem aktivniho letu vyzaduje HOLD.
- Vsechny pracovni kompozice maji cil ve stredu obrazu. STATIC otaci trup
  a gimbal, ale drzi polohu; FOLLOW povoluje i posuv za cilem.
- Dostupnost Virtual Stick se overuje po zapnuti. Odmitnuti povelu nebo
  chybejici odezva SDK vypne AI a zobrazi duvod. Prijeti povelu neni dukaz
  skutecneho posunu, ten musi potvrdit telemetrie a kontrolovany let.
- Predletovy duvod blokace zustava viditelny nad obrazem. Stare souradnice
  po ztrate kvality GNSS se nepovazuji za aktualni polohu dronu.
- Home z telefonu vyzaduje cerstvou polohu s presnosti do 10 m, telefon
  vedle dronu (do 30 m) a platnou polohu dronu. Satelity se neposuzuji
  pouze pevnou hranici 8: rozhoduje platna poloha a dobra kvalita DJI GNSS.
- Ulozeni Home zustava podmineno uspechem DJI a zpetnym prectenim bodu,
  RTH vysky, Smart RTH a failsafe. Zadny firmware se nemeni ani neobchazi.
- Zasah kniplem nebo koleckem gimbalu ma prednost, i behem aktivace AI.
  AI se po prevzeti pilotem sama neobnovi.

## Interni tester a rozsah overeni

FlightRuntimeSimulationTest pouziva skutecny FlightRuntime, SafetySupervisor,
FlightDirector, CameraDirector a VirtualStickPacket. Jen vystup DroneSession,
hodiny a prostredi jsou nahrazeny izolovanym testerem. Simulator nikdy
nevytvari DJI spojeni ani nevysila do dronu. Neni soucasti ovladaciho UI.

Tester integruje rychlosti do polohy a natoceni a promita cil zpet do obrazu
jednoduchym kamerovym modelem. Overuje centrovani a smer pohybu, vsechny
rezimy, jednoho/oba pracovniky, ztratu cile/polohy/telemetrie, slabou baterii,
RTH stav, odmitnuti SDK, timeout a pozdni odpoved pri prerusenem startu.
Home testy overuji validaci vstupu; nesimuluji skutecny firmware zapisujici
Home. Testy NEoveruji aerodynamiku, vitr, realnou detekci identity na videu,
prekazky, radiovy prenos, skutecne GPS, Android dialogy ani zapis na SD.

GitHub Actions spousti jednotkove testy a sestavuje demo i DJI APK.
Reporty jsou ulozene v artefaktu android-test-reports. Uspesny build ani
simulace nejsou potvrzenim bezpecnosti letu mezi lidmi nebo kolem budov.

## Prvni overeni s dronem

1. Nejprve bez vrtuli overit spojeni, zivy obraz, vyber jednoho cile,
   zobrazeni blokace a ulozeni Home venku. Neobchazet blokaci motoru.
2. Prvni pohybovou zkousku provest na volnem prostranstvi bez lidi a staveb
   v trase, s nabitou baterii a pilotem pripravenym okamzite prevzit rizeni.
3. Po overenem Home a povolenem vzletu zvolit nejnizsi rychlostni profil.
   STATIC musi menit smer/gimbal, nikoli misto; FOLLOW ma take menit polohu.
4. Pred slozitejsimi rezimy overit stop/HOLD a prevzeti kniplem. Az pote
   kratce testovat samostatne orbit, odjezd a stoupani.
5. Pri jakemkoli nesouhlasu smeru nebo stavu prerusit AI; nepokracovat
   v obletech kolem pracovniku. Tato verze nema overene vyhybani prekazkam.

## Podklady DJI

- https://developer.dji.com/mobile-sdk/documentation/introduction/component-guide-flightController.html
- https://developer.dji.com/api-reference/android-api/Components/FlightController/DJIFlightController.html
- https://developer.dji.com/api-reference/android-api/Components/FlightController/DJIFlightController_DJIFlightControllerCurrentState.html
