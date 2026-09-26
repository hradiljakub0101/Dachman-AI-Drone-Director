# Dachman AI Drone Director Android v0.15.10

## Změny

- REC už aplikace neoznačí za aktivní jen na základě přijetí příkazu SDK. Čeká na stav kamery DJI, že video skutečně nahrává. Pokud se potvrzení neobjeví do pěti sekund, aplikace nahlásí, že let není ověřen jako zaznamenávaný.
- Pohyb kniply vypne řízení Virtual Stick a předá dron pilotovi. Tento přechod neposílá příkaz STOP do kamery. Aplikace průběžně sleduje stav nahrávání; pokud kamera ohlásí přerušení, zobrazí upozornění.
- Orbit, Pull a stoupavý průlet už nejsou podmíněné Worker štítkem. Follow, statické sledování, Group a Rope nadále vyžadují odpovídající obrazový cíl.
- RTH zůstává nativní funkcí letového kontroléru DJI. Aplikace vyšle příkaz jen po ověření Home Pointu, předání Virtual Stick a dalších podmínek; za zahájený návrat ho označí až po potvrzení živou telemetrií.

## Záznam na kartě

DJI Mini 2 zapisuje video do microSD karty vložené v dronu. Živý náhled v aplikaci je samostatný přenos a neznamená, že soubor vzniká. SDK hlásí stav karty a stav záznamu kamery; aplikace neprohlíží obsah microSD jako počítač.

Před odletem:

1. V DJI Fly ověř, že kamera vidí kartu, její zbývající čas a režim videa.
2. Spusť krátký zkušební klip a sleduj potvrzený stav nahrávání na kameře.
3. Zastav klip tlačítkem STOP a přehraj jej v DJI Fly.
4. Po letu vypni dron a vyčkej, než kamera dokončí ukládání. Potom teprve kartu vyjmi.
5. Pokud aplikace ohlásí nepotvrzený REC, přerušení kamery nebo odpojení, nepovažuj let za zaznamenaný.

Nemaž ani neformátuj kartu před zálohou. Pokud DJI Fly potvrzuje záznam, ale počítač nenajde soubory, zkus čtečku, jiný adaptér a prohlédni celou kartu včetně složek s médii; formátování proveď až po záloze.

## Ověření a hranice

Automatické testy kontrolují stavový automat záznamu, odmítnuté potvrzení, návrat pilotovi a výběr režimů bez Worker štítků. CI sestavení neověřuje fyzický zápis na microSD, skutečné řízení konkrétního Mini 2, RTH v letu ani lety u stavby.

První zkoušku proveď na volném prostoru, s dronem na dohled a s rukou na ovladači. Zvlášť ověř REC, zásah do kniplu během záznamu, potvrzené STOP, přehrání uloženého souboru a samostatně RTH ve vzdálenosti nad dvacet metrů od Home Pointu. Pokud potvrzení DJI RTH chybí, převezmi řízení a nepokračuj v autonomních režimech.
