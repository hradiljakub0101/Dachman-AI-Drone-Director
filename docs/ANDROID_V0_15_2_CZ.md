# Android 0.15.2: zachovat ověřený Home Point při nepodporovaném Smart RTH

## Zjištění z videa 5963

- Aplikace ukazuje připojený Mini 2, 16 satelitů, GNSS souřadnice, signál ovladače 100 % a baterii 58 %.
- Karta Home hlásí přesně „Smart RTH nelze zapnout: Not supported“.
- Z implementace plyne, že se tato chyba objeví až po přijetí zápisu Home Pointu, jeho zpětném načtení a nastavení/ověření výšky RTH. Aplikace pak ale chybně označila celý Home Point jako neuložený. To zablokovalo vzlet a další kroky.

## Oprava

- Ověřený Home Point a RTH výška mají samostatnou platnost od volitelných funkcí Smart RTH a failsafe GO_HOME.
- Nepodporovaný Smart RTH nebo failsafe už nemaže potvrzený Home Point. Aplikace zobrazí stav těchto funkcí odděleně.
- Tlačítko návratu v aplikaci volá `startGoHome()` na stejném DJI Flight Controlleru, který zpracovává fyzické tlačítko RTH ovladače. Obě cesty používají aktuální Home Point uložený v letovém kontroléru.
- RTH po příkazu z aplikace je možné jen tehdy, když letový kontrolér potvrzuje Home Point. Pokud firmware odmítne samotné uložení nebo zpětné načtení, Home zůstane blokovaný.

## Testy a hranice ověření

- Jednotkové testy ověřují, že nepodporované Smart RTH a failsafe nesmažou ověřené souřadnice a výšku a že neplatná výška zůstává blokací.
- GitHub Actions spouští Android jednotkové a softwarové testy a sestavuje obě varianty APK.
- Video potvrzuje hlášku aplikace, nikoli fyzický pohyb nebo skutečný návrat. Fyzické tlačítko, synchronizaci stavu a let je nutné ověřit venku s dronem bezpečně na zemi a následně ve volném prostoru.
