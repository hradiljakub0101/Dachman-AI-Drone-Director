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

Demo varianta nemá SDK ani jeho oprávnění. DJI varianta umí registraci a připojení pro diagnostiku.
Oprávnění Androidu vyžaduje až při připojování, registrace bez nich neproběhne.

`Výběr manévru -> BiometricPrompt (silná biometrie nebo kód zařízení) -> jednorázový ApprovalGate -> výsledek simulace`

ApprovalGate váže výsledek na konkrétní revizi. Zrušení, změna manévru, opakované použití
nebo pozdní výsledek nemohou udělit schválení. onStop ruší záměr a dialog.

## Co toto ověření znamená

Prokazuje, že uživatel prošel systémovým ověřením držitele zařízení. Biometrické údaje ani
kód aplikace nečte a neukládá. Každý, kdo zná kód zařízení nebo má zaregistrovanou biometrii,
může projít. Nejde o ověření osobní identity jmenovaného pilota ani oprávnění létat.

Registrace uživatelských účtů, OAuth přihlášení, role, backend, refresh tokeny a serverové
odvolávání účtů nejsou implementované. Přihlášení GitHub konektoru slouží k vývoji, nikoli
k autentizaci uživatelů mobilní aplikace. DJI aktivace/binding účtu je samostatný SDK proces,
který tento prototyp zatím neimplementuje; diagnostika připojení ho nenahrazuje.

## Omezení řízení

Obě UI zůstávají prototypy. iOS SafetyContext používá ukázkové hodnoty a Android provádí
pouze schválení názvu manévru, nikoli výpočet letové trajektorie. Video/Vision/hlas zatím
nejsou do Androidu přeneseny. Živé řízení vyžaduje samostatné propojení telemetrie,
flight-core, override a hardwarové ověření. Schválení držitele telefonu tyto kroky nenahrazuje.
