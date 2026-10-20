# Prompt — Écran « Mes notifications » (historique persistant), app Android Kotlin

> À copier-coller tel quel à l'agent Claude qui travaille sur **Velox_client**
> (`dj.velox.client`, `C:\Users\PC\StudioProjects\Velox_client`).
>
> Contexte : le backend partagé (`nomade_client/functions`) et les règles
> Firestore viennent d'être étendus pour **persister** chaque notification
> envoyée par push dans `users/{uid}/notifications`, en plus du push FCM
> existant (qui ne change pas). L'app **iOS** (Flutter, autre repo) vient de
> livrer l'écran équivalent côté iOS. Ce prompt demande le même écran côté
> Android — même collection, mêmes règles, même contrat de données.
>
> Décisions déjà prises côté backend, à ne pas rediscuter :
> - l'historique couvre les 3 catégories : commandes food, courses VTC, promos ;
> - le statut lu/non-lu est **stocké côté serveur** (`read`/`readAt`), pour
>   suivre l'utilisateur d'un appareil à l'autre — pas de state local seul ;
> - rétention 90 jours (purge automatique côté serveur, rien à faire côté app) ;
> - le champ `body` contient le **texte complet**, jamais tronqué — à afficher
>   intégralement (pas d'ellipsis forcé dans le détail, seulement dans un
>   aperçu de liste si besoin).

---

## 1. Le contrat de données (déjà en production)

**Collection** : `users/{uid}/notifications/{notificationId}`, lecture seule
côté app sauf pour `read`/`readAt`.

```jsonc
{
  "type": "promo" | "order" | "ride",
  "title": "string",
  "body":  "string",
  "createdAt": Timestamp,
  "read":      boolean,        // false à la création
  "readAt":    Timestamp | null,
  "actionType":  "none" | "order" | "ride" | "restaurant" | "url" | "whatsapp",
  "actionValue": "string | null",   // orderId / rideId / restaurantId / URL / numéro
  "imageUrl":   "string | null",
  "campaignId": "string | null"
}
```

**Règles Firestore** (déjà déployées, ne pas modifier) :
```
match /notifications/{notificationId} {
  allow read:   if isAuth() && isOwner(userId);
  allow update: if isAuth() && isOwner(userId)
                && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['read', 'readAt']);
  allow create, delete: if false;
}
```
→ L'app peut **lire** et **basculer `read`/`readAt`**, jamais créer, modifier
un autre champ, ni supprimer. Toute tentative contraire sera rejetée côté
serveur — ne pas prévoir de fallback pour ça, ce n'est pas censé arriver.

**Index déployés** : `createdAt` DESC sur la sous-collection (requête
normale) — rien à configurer côté app, juste `orderBy("createdAt", DESCENDING)`.

---

## 2. Ce qui existe déjà dans le repo (à réutiliser, pas à réinventer)

- **`core/notifications/Notifications.kt`** — objet `VeloxNotifications`
  (channels `rides_v2`/`orders_v2`), `NotificationRouter` (singleton Hilt,
  `MutableStateFlow<DeepLink?>`), `DeepLink(type, orderId, rideId)`. C'est le
  mécanisme qui route un **push tapé depuis l'extérieur de l'app** (background/
  killed) vers le bon écran via `MainActivity.handleDeepLink()` →
  `navigation/VeloxNavGraph.kt`. Ce nouvel écran est **indépendant** de ce
  flux : c'est une page consultable depuis l'app, pas un handler de tap sur
  notification système. Ne pas le fusionner avec `DeepLink`/`NotificationRouter`.
- **Pattern liste Firestore + ViewModel + Compose**, à suivre à l'identique
  (illustré par l'historique des commandes) :
  - `data/remote/OrderService.kt` — service `@Singleton` injectant
    `FirebaseFirestore`, `callbackFlow` + `addSnapshotListener` +
    `awaitClose { registration.remove() }`, ex. `streamUserOrders(userId): Flow<List<Order>>`.
  - `feature/orders/OrdersViewModel.kt` — `@HiltViewModel`, expose un
    `StateFlow` via `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())`.
  - `feature/orders/OrderHistoryScreen.kt` — Compose pur (pas de XML),
    `LazyColumn`, `hiltViewModel()` + `collectAsStateWithLifecycle()`.
  - Domain model en `@Serializable data class` dans `domain/model/`, avec
    `fromFirestore(DocumentSnapshot)` (voir `Order.kt` pour le style).
  - `data/remote/AddressService.kt` — exemple d'accès à une sous-collection
    `users/{uid}/<sous-collection>` si besoin de retrouver la syntaxe exacte.
- **Point d'entrée naturel** : `feature/profile/ProfileScreen.kt`, section
  `// ── Notifications ──` (~ligne 356) — actuellement juste un
  `SectionHeader`. Ajouter une `SettingRow` dans le `SettingsGroup` suivant,
  sur le modèle exact de la ligne « Mes commandes » (~ligne 387-394,
  `onClick = onOpenOrders, trailing = { Chevron(c) }`).
- **Routes** : `object Routes` dans `navigation/VeloxNavGraph.kt` — ajouter
  une constante (ex. `Routes.NOTIFICATIONS`) et l'enregistrer dans
  `MainNavGraph` à côté de `composable(Routes.ORDERS) { OrderHistoryScreen(...) }`.
- **DI** : `di/FirebaseModule.kt` fournit déjà `FirebaseFirestore` — pas de
  base `FirestoreRepository` commune dans ce repo, chaque feature injecte
  directement `FirebaseFirestore` dans son propre service. Suivre cette
  convention pour le nouveau `NotificationsService`, pas d'abstraction en plus.

Aucun pattern de badge non-lu n'existe dans le repo aujourd'hui (le badge sur
le panier dans `feature/food/CartBar.kt` est le plus proche visuellement, mais
ne compte rien depuis Firestore) — c'est à construire.

---

## 3. Ce qu'il faut construire

1. **`domain/model/AppNotification.kt`** — data class + enums pour `type` et
   `actionType`, `fromFirestore(DocumentSnapshot)`. Pas besoin de `toMap()`
   complet côté client : seul un update partiel `{read, readAt}` est permis,
   inutile de sérialiser le reste.

2. **`data/remote/NotificationsService.kt`** — `@Singleton`, injecte
   `FirebaseFirestore` :
   - `streamNotifications(uid): Flow<List<AppNotification>>` —
     `users/{uid}/notifications`, `orderBy("createdAt", DESCENDING)`,
     `addSnapshotListener` (même pattern que `OrderService`).
   - `markAsRead(uid, notificationId)` — `update(mapOf("read" to true, "readAt" to FieldValue.serverTimestamp()))`.
     Ne rien envoyer d'autre : la règle Firestore rejette tout update qui
     touche un champ hors `read`/`readAt`.
   - (Optionnel, à votre appréciation) `markAllAsRead(uid)` — batch sur les
     docs `read == false` de la liste déjà chargée, même contrainte de champs.

3. **`feature/notifications/NotificationsViewModel.kt`** — `@HiltViewModel`,
   injecte le service + le repo d'auth pour le `uid` courant. Expose :
   - `notifications: StateFlow<List<AppNotification>>` (pattern `OrdersViewModel`) ;
   - `unreadCount: StateFlow<Int>` dérivé du même flow (`.map { list -> list.count { !it.read } }`)
     — **pas** un second listener Firestore, dérivez-le du flow déjà ouvert ;
   - `markAsRead(notificationId)`.

4. **`feature/notifications/NotificationsScreen.kt`** — Compose `LazyColumn`
   (pattern `OrderHistoryScreen`) :
   - Un item par notification : icône selon `type` (commande / course / promo),
     titre + `body` (texte complet), horodatage relatif, style visuellement
     différent si `read == false` (ex. point/pastille, poids de police).
   - Tap sur un item : `markAsRead()` si non lu, **puis** router selon
     `actionType`/`actionValue` :
     - `"order"` → écran de suivi commande existant, avec `actionValue` comme
       orderId (même destination que `Routes.orderTracking(id)`) ;
     - `"ride"` → écran de suivi course existant ; vérifiez si sa route a
       besoin d'un `rideId` explicite ou si elle lit l'état courant — passez
       `actionValue` si un paramètre est attendu ;
     - `"restaurant"` → écran détail restaurant existant avec `actionValue`
       comme restaurantId ;
     - `"url"` → ouvrir dans le navigateur (`Intent.ACTION_VIEW`) ;
     - `"whatsapp"` → ouvrir WhatsApp sur le numéro `actionValue` — regardez
       si un helper existe déjà ailleurs dans l'app pour contacter le support,
       le cas échéant réutilisez-le plutôt que de le réécrire ;
     - `"none"` → pas de navigation, la notification reste juste consultée
       (marquer lu suffit).
   - `actionValue == null` ou type imprévu → ne pas planter, ignorer la
     navigation silencieusement (les valeurs `actionType` sont fermées à la
     liste ci-dessus, mais soyez défensif).

5. **Point d'entrée + badge** : `SettingRow` "Mes notifications" dans
   `ProfileScreen.kt` (§2), avec le compteur `unreadCount` affiché en badge
   (pastille numérique) à côté du libellé ou sur le chevron. Route enregistrée
   dans `VeloxNavGraph.kt` (§2).

---

## 4. Ce qu'il ne faut pas faire

- Ne pas écrire dans `users/{uid}/notifications` autre chose que `read`/`readAt`
  — toute autre écriture est rejetée par les règles, inutile de la tenter.
- Ne pas fusionner ce nouvel écran avec le mécanisme `DeepLink`/`NotificationRouter`
  existant (tap sur push système) — ce sont deux flux indépendants qui peuvent
  pointer vers les mêmes écrans de destination, mais n'ont pas la même origine.
- Ne pas tronquer `body` dans le détail — c'est explicitement la raison d'être
  de cette persistance (le push, lui, reste tronqué côté OS).
- Ne pas inventer de nouvelles valeurs pour `type`/`actionType` — se limiter à
  celles listées au §1.
- Ne pas implémenter de purge côté client — la rétention 90 jours est gérée
  par une Cloud Function planifiée, déjà en production.

---

## 5. À vérifier avant de considérer fini

- `flutter analyze`/lint équivalent Kotlin (compilation propre, pas de warning
  nouveau introduit par cet écran).
- Test manuel : ouvrir la page avec des notifications existantes en base
  (il y en a déjà en prod depuis le déploiement backend), vérifier que le
  badge non-lu se met à jour en direct après un tap (sans pull-to-refresh),
  et que `read`/`readAt` sont bien persistés (rechargement de l'app → item
  reste marqué lu).
