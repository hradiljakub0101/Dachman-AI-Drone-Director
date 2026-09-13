# Autentizace a schválení manévru

## iOS

`DJI_APP_KEY (prostředí sestavení) -> inject_dji_key.py -> výsledný Info.plist -> DJI SDK`

UI i Objective-C bridge ověřují stejný bundle klíč. Úspěšné načtení klíče není úspěšná
registrace: rozhoduje callback `appRegisteredWithError`. Chyba nepokračuje k připojení.

`Náhled manévru -> LAContext deviceOwnerAuthentication -> kontrola revize záměru -> SafetySupervisor -> výsledek simulace`

Každé schválení vytváří nový LAContext. Neexistuje uložený příznak "pilot přihlášen".
HOLD, ABORT, změna vstupu a přechod aplikace do pozadí ruší rozpracované ověření.
Pozdní callback nesmí schválit jiný záměr. Neúspěch nebo nedostupná autentizace blokuje schválení.

## Android

`DJI_ANDROID_APP_KEY -> Gradle manifest placeholder -> com.dji.sdk.API_KEY -> registerApp callback`

Demo varianta nemá SDK ani jeho oprávnění. DJI varianta je plná letová aplikace pro
registraci, živý obraz, telemetrii, potvrzené akce a bezpečnostně omezené Virtual Stick řízení.
Oprávnění Androidu vyžaduje až při připojování, registrace bez nich neproběhne.

`Výběr akce -> animovaný náhled / bezpečnostní checklist -> BiometricPrompt -> jednorázový ApprovalGate -> živá telemetrie -> DJI příkaz`

ApprovalGate váže výsledek na konkrétní revizi. Zrušení, změna manévru, opakované použití
nebo pozdní výsledek nemohou udělit schválení. onStop ruší záměr a dialog.

## Co toto ověření znamená

Prokazuje, že uživatel prošel systémovým ověřením držitele zařízení. Biometrické údaje ani
kód aplikace nečte a neukládá. Každý, kdo zná kód zařízení nebo má zaregistrovanou biometrii,
může projít. Nejde o ověření osobní identity jmenovaného pilota ani oprávnění létat.

Registrace uživatelských účtů, OAuth přihlášení, role, backend, refresh tokeny a serverové
odvolávání účtů nejsou implementované. Přihlášení GitHub konektoru slouží k vývoji, nikoli
k autentizaci uživatelů mobilní aplikace. DJI aktivace/binding účtu je samostatný SDK proces,
který tento klient nepřebírá: aktivace a binding zůstávají samostatným procesem DJI SDK.

## Omezení řízení

iOS UI zůstává prototyp. Android propojuje živé DJI video, offline detekci osob,
telemetrii, omezený planner, SafetySupervisor, Virtual Stick i hardwarový RC override.
Vzlet, přistání a RTH vyžadují vlastní bezpečnostní checklist a nové ověření.
Schválení držitele telefonu nenahrazuje oprávnění pilota, kontrolu prostoru ani povinný
hardwarový akceptační test.
