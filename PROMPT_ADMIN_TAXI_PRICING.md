# Prompt — Écran "Tarifs VTC" côté admin (`NOMADE_ADMIN_COMPLETE`)

> À copier-coller tel quel à l'agent Claude qui travaille sur le repo admin (`ADMIN3/NOMADE_ADMIN_COMPLETE`, Flutter web).
> Contexte : jusqu'ici, le tarif VTC (prise en charge + prix/km) était codé en dur dans
> chaque app cliente (Android `Velox_client`, et bientôt iOS). Le backend
> (`nomade_client/functions/index.js`) et les apps clientes ont déjà été mis à jour pour
> lire/valider ce tarif depuis un document Firestore de config — **il ne reste que
> l'écran admin pour le piloter**, sur le modèle exact de l'écran "Bannières" déjà
> existant (`lib/screens/banners/`).

---

## Ce qui est déjà en place (fait, ne pas retoucher)

**Document Firestore `config/taxiPricing`** (un seul doc, pas de sous-collection) :
```json
{
  "standard": { "basePrice": 600, "includedKm": 3, "pricePerKm": 200 },
  "comfort":  { "basePrice": 750, "includedKm": 3, "pricePerKm": 200 }
}
```
- `basePrice` (FDJ) : prix de prise en charge, inclut déjà `includedKm` kilomètres.
- `includedKm` (km) : distance incluse dans `basePrice` avant que `pricePerKm` s'applique.
- `pricePerKm` (FDJ) : tarif au km au-delà de `includedKm`.
- Formule utilisée côté serveur et clients : `prix = basePrice + pricePerKm × max(0, distanceKm − includedKm)`.
- Le doc **n'existe pas encore en prod** — les Cloud Functions et les apps clientes ont
  toutes un repli sur ces mêmes valeurs par défaut en son absence, donc rien n'est cassé
  tant que l'écran admin n'a pas été utilisé pour le créer.

**Règles Firestore** (déjà déployées) — collection `config` : `allow read: if isAuth()`,
`allow write: if isAdmin()` (même politique que `banners`).

**Revalidation serveur** (déjà en place, ne pas dupliquer côté admin) :
`nomade_client/functions/index.js` recalcule le tarif à la création d'une course
(`onTaxiRideCreated`) et à sa complétion (`onRideUpdated`) depuis ce même document — les
valeurs que tu vas rendre éditables ici sont donc la **source de vérité unique**, aucune
validation supplémentaire à écrire côté admin.

---

## Ce qu'il faut construire

Reproduire exactement le pattern déjà utilisé pour l'écran `banners/` (`BannerItem` /
`BannerService` / `BannersScreen`), mais pour un **document unique** (pas une collection
de plusieurs items, pas de glisser-déposer, pas d'upload d'image) :

### 1. `lib/models/taxi_pricing.dart`
```dart
class TaxiPricingTier {
  final double basePrice;
  final double includedKm;
  final double pricePerKm;

  const TaxiPricingTier({
    required this.basePrice,
    required this.includedKm,
    required this.pricePerKm,
  });

  factory TaxiPricingTier.fromMap(Map<String, dynamic>? data) => TaxiPricingTier(
        basePrice: (data?['basePrice'] as num?)?.toDouble() ?? 0,
        includedKm: (data?['includedKm'] as num?)?.toDouble() ?? 0,
        pricePerKm: (data?['pricePerKm'] as num?)?.toDouble() ?? 0,
      );

  Map<String, dynamic> toMap() => {
        'basePrice': basePrice,
        'includedKm': includedKm,
        'pricePerKm': pricePerKm,
      };

  TaxiPricingTier copyWith({double? basePrice, double? includedKm, double? pricePerKm}) =>
      TaxiPricingTier(
        basePrice: basePrice ?? this.basePrice,
        includedKm: includedKm ?? this.includedKm,
        pricePerKm: pricePerKm ?? this.pricePerKm,
      );
}

/// Valeurs par défaut si le document Firestore n'existe pas encore — DOIVENT rester
/// synchronisées avec `DEFAULT_TAXI_PRICING` dans nomade_client/functions/index.js.
const kDefaultStandardTier = TaxiPricingTier(basePrice: 600, includedKm: 3, pricePerKm: 200);
const kDefaultComfortTier = TaxiPricingTier(basePrice: 750, includedKm: 3, pricePerKm: 200);

class TaxiPricingConfig {
  final TaxiPricingTier standard;
  final TaxiPricingTier comfort;

  const TaxiPricingConfig({required this.standard, required this.comfort});

  factory TaxiPricingConfig.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>? ?? {};
    return TaxiPricingConfig(
      standard: data['standard'] != null
          ? TaxiPricingTier.fromMap(data['standard'] as Map<String, dynamic>)
          : kDefaultStandardTier,
      comfort: data['comfort'] != null
          ? TaxiPricingTier.fromMap(data['comfort'] as Map<String, dynamic>)
          : kDefaultComfortTier,
    );
  }

  static const defaults = TaxiPricingConfig(standard: kDefaultStandardTier, comfort: kDefaultComfortTier);

  Map<String, dynamic> toMap() => {'standard': standard.toMap(), 'comfort': comfort.toMap()};
}
```

### 2. `lib/services/taxi_pricing_service.dart`
Mirroir de `banner_service.dart`, mais un seul document (`.doc('taxiPricing')`,
`.snapshots()` sur le doc et non une query, `.set(..., SetOptions(merge: true))` pour
créer le doc s'il n'existe pas encore) :
```dart
import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/taxi_pricing.dart';

class TaxiPricingService {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  DocumentReference get _doc => _firestore.collection('config').doc('taxiPricing');

  Stream<TaxiPricingConfig> watchPricing() {
    return _doc.snapshots().map((snap) =>
        snap.exists ? TaxiPricingConfig.fromFirestore(snap) : TaxiPricingConfig.defaults);
  }

  Future<void> savePricing(TaxiPricingConfig config) async {
    await _doc.set({
      ...config.toMap(),
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
  }
}
```

### 3. `lib/screens/taxi_pricing/taxi_pricing_screen.dart`
`StatefulWidget` avec un `StreamBuilder<TaxiPricingConfig>` sur `watchPricing()`, qui
alimente un formulaire simple — **deux cartes côte à côte (ou empilées sur mobile,
regarde comment `isMobile(context)` est utilisé dans `banners_screen.dart`)**, une par
véhicule ("Standard", "Confort"), chacune avec 3 champs numériques :
- Prix de prise en charge (FDJ)
- Km inclus dans ce prix
- Prix par km au-delà (FDJ)

Un bouton "Enregistrer" en bas (ou par carte) qui appelle `savePricing(...)`. Affiche un
aperçu live du prix pour une distance repère (ex. 5 km et 10 km) sous chaque carte, pour
que l'admin visualise l'effet du changement avant de sauvegarder — genre :
`"Aperçu : 5 km → {prix} FDJ · 10 km → {prix} FDJ"`, recalculé à chaque frappe.

Style à réutiliser tel quel (`constants.dart`) : `defaultPadding`, `cardColor`,
`primaryColor`, `captionStyle`, mêmes `ElevatedButton.styleFrom(...)` que dans
`banners_screen.dart`.

### 4. Navigation — brancher le nouvel écran
- `lib/widgets/sidebar.dart` (à côté de l'entrée `'banners'`, ligne ~140) : ajouter un
  `_buildMenuItem(icon: Icons.local_taxi_outlined, title: 'Tarifs VTC', page: 'taxiPricing')`.
- `lib/screens/dashboard/dashboard_screen.dart` : ajouter `case 'taxiPricing': return const TaxiPricingScreen();`
  dans le switch qui résout le widget (~ligne 148, à côté de `case 'banners'`) **et** dans
  le switch qui résout le titre de l'AppBar (~ligne 272, `case 'taxiPricing': return 'Tarifs VTC';`).

---

## Ne pas faire

- Ne pas recalculer/valider un prix de course depuis l'admin — cet écran ne fait
  qu'éditer la config, la validation d'une course réelle reste entièrement côté Cloud
  Function (`onTaxiRideCreated` / `onRideUpdated`), déjà en place.
- Ne pas toucher aux règles Firestore ni aux Cloud Functions — tout est déjà déployé
  côté backend, seul l'écran admin manque.
- Ne pas ajouter d'autres véhicules ("van", "premium"...) : seuls `standard` et `comfort`
  existent aujourd'hui côté clients (`RideType` ne propose que ces deux choix à
  l'utilisateur pour l'instant).
