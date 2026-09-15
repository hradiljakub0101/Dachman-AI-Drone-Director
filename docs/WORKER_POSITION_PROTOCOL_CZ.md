# Protokol polohových tagů Worker 1 a Worker 2

Aplikace přijímá polohu pouze přes chráněný Android broadcast. Odesílající doprovodná
aplikace nebo UWB brána musí být podepsaná stejným certifikátem a požadovat oprávnění
`cz.dachman.drone.director.permission.WORKER_POSITION`.

## Zpráva

- action: `cz.dachman.drone.director.WORKER_POSITION`
- `worker_id`: stabilní textový identifikátor tagu
- `latitude`: zeměpisná šířka typu double
- `longitude`: zeměpisná délka typu double
- `accuracy_meters`: odhad přesnosti typu float
- `source`: `PHONE_GNSS` nebo `UWB_FUSED`

Čas pozorování určuje přijímající aplikace monotónními hodinami telefonu, aby nešlo
podvrhnout čerstvost dat. Po prvním přijetí pilot zadá stejné ID do panelu a ručně je
spáruje s obrazovým rámečkem Worker 1 nebo Worker 2.

## Bezpečnostní chování

- Obraz zůstává hlavním zdrojem pro kompozici, gimbal a zoom.
- Poloha tagu poskytuje omezenou predikci směru a udržení původního relativního odsazení.
- GNSS fix s přesností horší než osm metrů a UWB-fused fix horší než dva metry vyvolá HOLD.
- Data starší než dvě sekundy vyvolají HOLD.
- Vzdálenost menší než šest metrů vypne AI řízení.
- Vzdálenost větší než padesát metrů vyvolá HOLD.
- Zásah kniplem nebo kolečkem gimbalu zůstává nadřazený a vyžaduje ruční opětovné zapnutí AI.

Hodnoty jsou konzervativní softwarové pojistky, nikoli potvrzení bezpečnosti konkrétního
pracoviště. Před ostrým použitím musí následovat pozemní test, let bez osob a řízená zkouška.
