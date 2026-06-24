# Velox Client (Android natif)

Application cliente **Velox** — taxi (VTC) + livraison de repas à Djibouti.
Réécriture native **Kotlin / Jetpack Compose** de l'app Flutter `nomade_client`
(backend Firebase **partagé**, schéma Firestore inchangé). Design « Kinetic Monolith »
(dark + vert néon `#9FFF88`).

> L'app Flutter reste pour iOS ; ce client natif remplace l'Android pour la performance.

## Stack

- **UI** : Jetpack Compose + Material 3
- **DI** : Hilt · **Async** : Coroutines/Flow · **Persistance** : DataStore
- **Backend** : Firebase (Auth, Firestore, Storage, Functions, Messaging, Crashlytics)
- **Cartes** : MaplibreGL + tuiles CartoDB · **Itinéraires** : OpenRouteService · **Recherche** : Nominatim
- **Images** : Coil · **Connexion Google** : Credential Manager
- minSdk 24 · target/compileSdk 36 · Java 11 · AGP 9.1 (Kotlin intégré) · KSP · Hilt

## Configuration locale requise (non versionnée)

Ces fichiers sont volontairement exclus du dépôt (voir `.gitignore`) :

| Fichier | Rôle |
|---|---|
| `app/google-services.json` | Config Firebase Android (`dj.velox.client`) |
| `local.properties` | `sdk.dir` + **`ORS_API_KEY=<clé OpenRouteService>`** (injectée via `BuildConfig`) |
| `key.properties` + `*.keystore` | Signature release (clé Velox) |

Sans `ORS_API_KEY`, le calcul d'itinéraire retombe automatiquement sur un tracé en ligne droite.

## Build

```bash
./gradlew assembleDebug          # APK debug
./gradlew testDebugUnitTest      # tests unitaires
./gradlew assembleRelease        # APK release signé + R8
./gradlew bundleRelease          # AAB (Play Store)
```

## Internationalisation

5 langues : Français (défaut), English, العربية (RTL), Af-Soomaali, Qafar af.
Changement de langue à chaud via le profil (`AppCompatDelegate.setApplicationLocales`).
Les traductions Somali (`values-so`) et Afar (`values-aa`) sont à faire relire par un locuteur natif
(blocs marqués `À RELIRE`).
