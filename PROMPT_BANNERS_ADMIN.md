# Prompt — Bannières promo pilotées depuis l'admin (`Velox_client`, Kotlin)

> À copier-coller tel quel à l'agent qui travaille sur `Velox_client`.
> Contexte : le carrousel de bannières en haut de l'accueil Food est actuellement
> codé en dur (`FoodHomeScreen.kt`). L'admin peut désormais créer/gérer ces
> bannières (texte ou image) depuis le web — cette session doit remplacer le
> carrousel en dur par une lecture Firestore temps réel. Ne pas toucher aux
> Cloud Functions ni aux règles Firestore/Storage : c'est fait côté admin
> (⚠️ **à vérifier que l'admin a bien copié les nouvelles règles dans la
> console Firebase avant de tester** — sinon la lecture de `banners` échouera).

---

## Ce qui existe déjà côté backend (fait, ne pas retoucher)

**Collection `banners/{id}`** (doc ID auto), créée/gérée depuis l'admin Flutter web,
triable par glisser-déposer :
```
{
  type: "text" | "image",
  order: number,             // ordre d'affichage dans le carrousel
  active: boolean,
  // si type == "text"
  title: string,
  subtitle: string,
  // si type == "image"
  imageUrl: string,          // URL Storage publique (via getDownloadURL)
  storagePath: string,       // chemin Storage (pas utile côté client)
  // commun aux deux types
  actionType: "none" | "whatsapp" | "url" | "restaurant" | "category",
  actionValue: string | null,  // numéro WhatsApp / URL / restaurantId / nom de catégorie
  createdAt, updatedAt,
}
```

**Règles Firestore** : `allow read: if isAuth()` (n'importe quel utilisateur connecté
peut lire, rien de sensible), `allow write: if isAdmin()`.

**Pas de multi-langue** : le titre/sous-titre est un texte unique (pas un par
langue comme les anciennes `strings.xml` `promo_title`/`promo2_title`/etc.) — décision
prise pour garder l'admin simple. Le texte s'affichera tel quel, peu importe la
langue du téléphone.

## Ce qu'il reste à faire côté `Velox_client`

### 1. Nouveau modèle `domain/model/Banner.kt`
Mirroir du style de `Restaurant.kt`/`Order.kt` (factory `fromFirestore`, defaults
tolérants) :
```kotlin
data class Banner(
    val id: String = "",
    val type: String = "text",       // "text" | "image"
    val order: Int = 0,
    val active: Boolean = true,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val actionType: String = "none", // "none" | "whatsapp" | "url" | "restaurant" | "category"
    val actionValue: String? = null,
) {
    val isImage: Boolean get() = type == "image"

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): Banner {
            val data = doc.data ?: emptyMap()
            return Banner(
                id = doc.id,
                type = data["type"] as? String ?: "text",
                order = (data["order"] as? Number)?.toInt() ?: 0,
                active = data["active"] as? Boolean ?: true,
                title = data["title"] as? String,
                subtitle = data["subtitle"] as? String,
                imageUrl = data["imageUrl"] as? String,
                actionType = data["actionType"] as? String ?: "none",
                actionValue = data["actionValue"] as? String,
            )
        }
    }
}
```

### 2. Nouveau service `data/remote/BannerService.kt`
Mirroir exact de `RestaurantService.kt` (même style `callbackFlow` +
`addSnapshotListener`). **Important — évite un index composite Firestore** (même
convention que le reste du projet, cf. commentaires dans `restaurant_service.dart`
côté admin) : trie par `order` côté Firestore (`orderBy`, champ unique → pas d'index
composite requis), et filtre `active == true` **côté Kotlin**, pas dans la requête :
```kotlin
@Singleton
class BannerService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun streamActiveBanners(): Flow<List<Banner>> = callbackFlow {
        val reg = firestore.collection("banners")
            .orderBy("order")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val banners = snapshot?.documents
                    ?.mapNotNull { runCatching { Banner.fromFirestore(it) }.getOrNull() }
                    ?.filter { it.active }
                    ?: emptyList()
                trySend(banners)
            }
        awaitClose { reg.remove() }
    }
}
```

### 3. `feature/food/FoodViewModels.kt`
Dans `RestaurantsViewModel` (ligne ~32), ajouter le service et exposer les bannières,
même pattern que `restaurants`/`popular` (lignes 36-43) :
```kotlin
class RestaurantsViewModel @Inject constructor(
    service: RestaurantService,
    private val menuService: MenuService,
    bannerService: BannerService,   // ← nouveau paramètre
) : ViewModel() {
    val banners: StateFlow<List<Banner>> = bannerService.streamActiveBanners()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // ... reste inchangé
```

### 4. `feature/food/FoodHomeScreen.kt` — remplacer le carrousel en dur
- **Supprimer** le `promoSlides` codé en dur (lignes 98-110) et la classe `PromoSlide`
  (lignes 300-305).
- Récupérer les bannières : `val banners by restaurantsVm.banners.collectAsStateWithLifecycle()`
  (à côté de `restaurants`/`popular`/`categories`, ligne ~111-113).
- Le carrousel (ligne 155-159) devient conditionnel — **ne rien afficher si la liste
  est vide** (contrairement à l'ancien code qui affichait toujours 3 slides) :
  ```kotlin
  if (banners.isNotEmpty()) {
      item {
          AutoScrollCarousel(items = banners, height = 138.dp, pageFraction = 1f, c = c) { banner ->
              if (banner.isImage) ImageBanner(banner, c) { handleBannerAction(banner, context, onOpenRestaurant, onOpenCategory) }
              else TextBanner(banner, c) { handleBannerAction(banner, context, onOpenRestaurant, onOpenCategory) }
          }
      }
      item { Spacer(Modifier.height(28.dp)) }
  }
  ```
- **`TextBanner`** : reprend le style de l'actuel `PromoBanner` (ligne 307-329), mais
  lit `banner.title`/`banner.subtitle` (String direct, plus `stringResource`) au lieu
  des `@StringRes`. Garder la même icône de fond par défaut (`Icons.Filled.LocalOffer`
  ou équivalent générique — l'admin ne choisit pas d'icône).
- **`ImageBanner`** (nouveau) : même conteneur (`fillMaxWidth().fillMaxHeight()`,
  `clip(RoundedCornerShape(18.dp))`, `clickable` si action), mais avec
  `AsyncImage(model = banner.imageUrl, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())`
  en fond — `coil.compose.AsyncImage` est déjà importé dans ce fichier (ligne 69,
  utilisé par `PopularCard`), pas de nouvelle dépendance.
- **`handleBannerAction(banner, context, onOpenRestaurant, onOpenCategory)`** —
  fonction qui remplace le `onClick` en dur (actuellement seule la 3e bannière ouvre
  WhatsApp, ligne 102-108) :
  ```kotlin
  private fun handleBannerAction(
      banner: Banner,
      context: android.content.Context,
      onOpenRestaurant: (String) -> Unit,
      onOpenCategory: (String) -> Unit,
  ) {
      val value = banner.actionValue ?: return
      when (banner.actionType) {
          "whatsapp" -> runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$value")))
          }
          "url" -> runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
          }
          "restaurant" -> onOpenRestaurant(value)
          "category" -> onOpenCategory(value)
          else -> Unit
      }
  }
  ```
  `onOpenRestaurant`/`onOpenCategory` sont **déjà des paramètres de `FoodHomeScreen`**
  (lignes 87-88) — pas de nouvelle navigation à créer.

### 5. Nettoyage optionnel (pas bloquant)
Les chaînes `promo_title`/`promo_subtitle`/`promo2_*`/`promo3_*` dans les 5
`strings.xml` (`values`, `values-en`, `values-ar`, `values-so`, `values-aa`)
deviennent orphelines si plus rien ne les référence — vérifie d'abord qu'aucun
autre écran ne les utilise avant de les supprimer.

## Ne pas faire
- Ne pas ajouter `.whereEqualTo("active", true)` à la requête Firestore (index
  composite requis) — filtrer `active` en Kotlin après lecture, comme montré ci-dessus.
- Ne pas essayer de gérer plusieurs langues pour `title`/`subtitle` — c'est une
  décision produit déjà prise, un seul texte par bannière.
- Ne pas toucher aux règles Firestore/Storage ni aux Cloud Functions.
