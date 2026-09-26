# Dachman AI Drone Director Android v0.15.11

## Změny oproti v0.15.10

- Maximální délka Orbitu se prodloužila na třicet pět sekund.
- Maximální délka Pull Away se prodloužila na patnáct sekund.
- Maximální délka stoupavého odletu se prodloužila na dvanáct sekund.
- Rychlostní profily, výškový limit, požadavek na potvrzení pilota a zásah kniply zůstávají aktivní. Pilot může každý záběr kdykoli zastavit přes HOLD nebo převzít ovladač.
- Záznam se nadále považuje za spuštěný až po potvrzení živým stavem kamery DJI.

## Zkouška

Android SITL testy ověřují, že Orbit, Pull Away a stoupavý odlet zůstávají aktivní po dvanácti simulovaných sekundách, vydávají pohybové povely a lze je zastavit přes HOLD. Simulace neověřuje aerodynamiku, GPS přesnost, překážky, microSD zápis ani RTH na fyzickém DJI Mini 2.

První let testuj na volném prostranství mimo osoby, budovy, vedení, střechy a jiné překážky. Prodloužený čas znamená delší pohyb. Za letu drž RC-N1 připravený k převzetí a ověř záběry vždy jednotlivě před prací u objektu.
