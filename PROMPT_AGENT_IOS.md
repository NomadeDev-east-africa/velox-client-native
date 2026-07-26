# Prompt — Agent Claude Code iOS / Flutter (`nomade_client`)

> À copier-coller tel quel à l'agent qui travaille sur l'app Flutter cliente.
> Contexte : les mêmes 2 corrections ont déjà été appliquées et validées sur l'app
> Kotlin `Velox_client`. Ce document sert de référence de travail côté iOS/Flutter.

---

**Contexte** : App Flutter cliente `nomade_client` (`C:\Users\PC\StudioProjects\nomade_client`),
backend Firebase partagé avec l'app Kotlin `Velox_client` (déjà corrigée et validée).
Deux corrections à apporter. **Ne touche PAS aux Cloud Functions** (`functions/index.js`) :
elles sont correctes et testées en prod sur Android.

## Correction 1 — Extras/sauces en dur (Dart partagé Android + iOS)

Fichier : `lib/screens/food/addToOrder/add_to_order_screen.dart`

**Problème** : quand un plat n'a pas d'`optionGroups`, l'écran injecte des extras/sauces
**codés en dur** (Frites, Tomates, Oignons, Salade, Taille L/XL/XXL à +500 FDJ et sauces
Samouraï/Mayo/Ketchup… à +50 FDJ) via `_initializeExtrasAndSauces()` (lignes ~65-83).
Ces options n'existent pas réellement pour le plat → affichage faux (ex. « BOWL STEAK »).

**Règle voulue** (identique au fix Kotlin déjà appliqué) :
- Plat **avec** `optionGroups` (`_isDataDriven == true`) → afficher les vraies options
  (FORMULE, SUPPLÉMENTS…). Inchangé.
- Plat **sans** `optionGroups` → **n'afficher AUCUNE section d'options**. Pas d'extras
  ni de sauces par défaut. Juste image + prix de base + quantité + bouton « Ajouter au panier ».

**À faire** :
1. Supprimer le corps de `_initializeExtrasAndSauces()` (ou l'appel ligne ~49) pour que
   `_extras` et `_sauces` restent **vides**.
2. Vérifier que les widgets de construction des sections extras/sauces (autour des lignes
   ~600 et ~751, `itemCount: _sauces.length`, `..._extras.map(...)`) ne rendent **rien**
   quand la liste est vide — masquer aussi les en-têtes de section « CHOIX DES EXTRAS » /
   « SAUCES » (ne pas afficher un titre au-dessus d'une liste vide).
3. `_optionsSurcharge` (ligne ~90) : la branche `else` doit valoir `0` quand pas d'options.
4. Le `OrderItem` construit (lignes ~218-219) doit alors avoir `extras: []` et `sauces: []`.
5. Nettoyer les imports/champs devenus inutilisés (`ExtraOption`, `SauceOption`, `_extras`,
   `_sauces`, `_extrasTotal`, `_saucesTotal`) sans casser la compilation.

## Correction 2 — Notifications iOS

**Symptôme** : le client iOS ne reçoit pas les push (ni VTC « chauffeur arrivé », ni resto
« commande prête »). Sur Android c'était la permission runtime ; sur iOS les causes diffèrent.

**Déjà vérifié et CORRECT côté Dart, ne pas le refaire** :
- `lib/services/notification_service.dart:250` écrit bien `users/{uid}.fcmToken` — **même
  champ exact que les Cloud Functions lisent** (`getClientFcmToken` → `users/{userId}.fcmToken`).
  Path OK.
- `requestPermission()` (ligne ~60) et `getToken()` (ligne ~205) existent.

**À diagnostiquer/corriger, spécifique iOS** (par ordre de probabilité) :
1. **Clé APNs (.p8) dans Firebase Console** → *Project Settings → Cloud Messaging → Apple
   app configuration*. Sans clé d'authentification APNs, FCM **ne peut rien livrer** à iOS
   (cause n°1 de « OK sur Android, rien sur iOS »). Vérifier qu'elle est uploadée pour le
   bon Team ID / Key ID / Bundle ID.
2. **Capabilities Xcode** (`ios/Runner.xcworkspace`) : activer **Push Notifications** ET
   **Background Modes → Remote notifications**. Vérifier `Runner.entitlements` (`aps-environment`).
3. **Token APNs avant getToken** : sur iOS, `getToken()` peut renvoyer `null` si le token
   APNs n'est pas encore prêt (race au 1er lancement). Ajouter un
   `await FirebaseMessaging.instance.getAPNSToken()` non-null (avec retry/attente) **avant**
   `getToken()`, et re-tenter l'enregistrement du token si null. Il n'y a actuellement aucun
   appel à `getAPNSToken()` dans le code.
4. **Affichage au premier plan** : appeler
   `FirebaseMessaging.instance.setForegroundNotificationPresentationOptions(alert: true, badge: true, sound: true)`
   (sinon rien ne s'affiche quand l'app est ouverte).
5. **AppDelegate.swift** : vérifier `UNUserNotificationCenter.current().delegate = self` et
   le pont FCM (`FirebaseAppDelegateProxyEnabled` ou passage manuel du token APNs à
   `Messaging.messaging().apnsToken`).
6. **Tester sur un vrai iPhone** (le simulateur iOS ne reçoit pas les push APNs).

**Test de validation bout-en-bout** (iPhone réel) :
- Passer une commande → app resto la met « Prête » → le client iOS doit recevoir
  « ✅ Votre commande est prête ».
- Créer une course → chauffeur passe en `arrived` → le client iOS doit recevoir
  « 📍 Votre chauffeur est arrivé ».

---

## Rappel — côté restaurant (pour le test)

1. App resto : **se déconnecter / reconnecter** pour rafraîchir `restaurants/{id}.fcmToken`
   (un token périmé = pas de push, correctif = re-login, pas de code).
2. C'est l'action **« Prête »** dans l'app resto qui déclenche la notif client
   (`sendOrderReadyNotifications`).
