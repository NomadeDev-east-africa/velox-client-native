# Prompt — Code promo au checkout (app cliente `Velox_client`, Kotlin)

> À copier-coller tel quel à l'agent qui travaille sur `Velox_client`.
> Contexte : le backend et l'admin sont déjà faits et déployés/déployables — cette
> session ne doit toucher QUE l'app cliente Kotlin. Ne pas modifier
> `functions/index.js` (nomade_client) ni les règles Firestore : c'est fait.

---

## Ce qui existe déjà côté backend (fait, ne pas retoucher)

**Collection `promoCodes/{CODE}`** (doc ID = le code, toujours en MAJUSCULES),
créée depuis l'admin Flutter web :
```
{
  code: string,                 // = doc ID
  discountType: "percentage" | "fixed",
  discountValue: number,        // % (0-100) si percentage, FDJ si fixed
  restaurantId: string | null,  // null = valable sur tous les restaurants
  restaurantName: string | null,
  expiresAt: Timestamp | null,  // null = pas d'expiration
  active: boolean,
  usageCount: number,           // écrit uniquement par la Cloud Function
}
```
**Règles Firestore : le client N'A PAS le droit de lire `promoCodes` ni
`promoCodeRedemptions` directement** (`allow read: if false` / `isAdmin()` uniquement).
Tout passe par les Cloud Functions ci-dessous — normal, ne pas contourner.

**Cloud Function callable `validatePromoCode`** (auth requise, même mécanique que
`sendRestaurantNotification` déjà appelée dans `OrderService.kt:59` via
`functions.getHttpsCallable(...)`) :

Input :
```kotlin
mapOf(
    "code" to code,                 // ce que l'utilisateur a tapé, n'importe quelle casse
    "restaurantId" to restaurantId,
    "subtotal" to subtotal,         // Int, somme des items du panier
    "deliveryFee" to deliveryFee,   // Int
)
```
Output (deux formes possibles) :
```jsonc
// Refusé
{ "valid": false, "message": "Ce code promo a expiré" }
// Accepté
{ "valid": true, "code": "WELCOME10", "discountAmount": 450, "discountType": "percentage", "discountValue": 10 }
```
`discountAmount` est déjà calculé et plafonné côté serveur (jamais négatif, jamais
> subtotal + deliveryFee) — c'est le seul nombre à afficher, pas la peine de
recalculer côté client.

**Trigger `onOrderCreated` (déjà étendu)** : si la commande écrite contient un champ
`promoCode`, le serveur **réévalue tout depuis zéro** (code actif ? pas expiré ? pas
déjà utilisé par cet utilisateur — 1 utilisation par client ? bon restaurant ?) et
corrige `promoDiscount`/`total` si besoin — exactement le même filet de sécurité que
`pointsUsed`/`discount` (fidélité) qui existe déjà dans `Order.kt`. Si le code est
invalide à ce moment-là (ex: quelqu'un d'autre l'a utilisé entre-temps), la remise est
silencieusement remise à 0 et le total corrigé — pas d'erreur bloquante à ce stade,
comme pour les prix des plats.

## Ce qu'il reste à faire côté `Velox_client`

### 1. `domain/model/Order.kt`
Mirror exact du pattern `pointsUsed`/`discount` déjà présent :
- Ajouter `val promoCode: String? = null` et `val promoDiscount: Int = 0` au data class.
- `total` (ligne 47) : `subtotal + deliveryFee - discount - promoDiscount`, avec
  `.coerceAtLeast(0)`.
- `toMap()` (ligne 57+) : ajouter `promoCode?.let { put("promoCode", it) }` et
  `put("promoDiscount", promoDiscount)`.
- `fromFirestore()` (ligne 103+) : lire `promoCode` (String?) et `promoDiscount`
  (Number → Int, défaut 0), même style que `pointsUsed`/`discount` juste au-dessus.

### 2. `data/remote/OrderService.kt`
Ajouter une fonction qui appelle le callable, sur le modèle de `notifyRestaurant()`
(lignes 48-73) :
```kotlin
suspend fun validatePromoCode(
    code: String,
    restaurantId: String,
    subtotal: Int,
    deliveryFee: Int,
): PromoValidationResult = runCatching {
    val result = functions.getHttpsCallable("validatePromoCode").call(
        mapOf(
            "code" to code,
            "restaurantId" to restaurantId,
            "subtotal" to subtotal,
            "deliveryFee" to deliveryFee,
        ),
    ).await()
    @Suppress("UNCHECKED_CAST")
    val data = result.getData() as Map<String, Any?>
    // mapper vers un data class PromoValidationResult(valid, message?, discountAmount?, code?)
}.getOrElse { PromoValidationResult(valid = false, message = "Erreur réseau, réessayez") }
```
(nom/emplacement du data class au choix — rester cohérent avec le reste du fichier.)

### 3. `feature/food/CartViewModel.kt` + `CartScreen.kt`
- État : `appliedPromoCode: String?`, `promoDiscountAmount: Int`, `promoError: String?`,
  `isValidatingPromo: Boolean` dans `CartUiState` (même zone que `pointsUsed`, ligne 60).
- Fonction `applyPromoCode(code: String)` : appelle `orderService.validatePromoCode(...)`
  avec le `subtotal`/`deliveryFee` courants du panier ; si `valid == true`, stocke
  `discountAmount` + le code normalisé ; sinon affiche `message` en erreur (pas
  bloquant, l'utilisateur peut réessayer un autre code).
- **Revalider si le panier ou le restaurant change** après application (comme un
  changement de `subtotal` invaliderait un pourcentage déjà calculé) — le plus simple :
  effacer `appliedPromoCode`/`promoDiscountAmount` dès que le panier change après
  application, et laisser l'utilisateur retaper le code.
- `createOrder(...)` (ligne 199) : ajouter les paramètres `promoCode: String? = null`,
  `promoDiscountAmount: Int = 0`, les passer au constructeur `Order(...)` (ligne 231),
  exactement comme `pointsUsed`/`discount` (lignes 248-249) juste au-dessus.
- UI `CartScreen.kt` : un champ texte + bouton « Appliquer » (section résumé de
  commande, à côté d'où s'affiche la réduction fidélité si elle existe visuellement) ;
  si appliqué, afficher la ligne « Code promo (XXXX) : -450 FDJ » dans le récapitulatif
  et un bouton pour le retirer.

### 4. Écran de confirmation finale
Avant d'appeler `createOrder`, si un code est appliqué, il est recommandé (pas
obligatoire) de rappeler `validatePromoCode` une dernière fois juste avant l'envoi
pour réduire la fenêtre de course (le serveur corrige de toute façon, mais un
re-check évite d'afficher au client un total qui bouge après coup).

## Ne pas faire
- Ne pas lire `promoCodes` ou `promoCodeRedemptions` directement en Firestore (bloqué
  par les règles, de toute façon).
- Ne pas calculer soi-même le pourcentage/montant de réduction pour l'afficher comme
  valeur de vérité — toujours celle renvoyée par `validatePromoCode`.
- Ne pas toucher à `functions/index.js` ni aux règles Firestore.
