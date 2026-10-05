# ============================================================
# Velox client — règles ProGuard/R8 (build release minifié)
# ============================================================

# Garde les numéros de ligne pour des stack traces lisibles (Crashlytics futur).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ── kotlinx.serialization ────────────────────────────────────
# Les modèles @Serializable sont (dé)sérialisés par serializer généré : on garde
# les companions/serializers, sinon R8 casse le cache panier/commande/course.
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Modèles & énumérations du domaine (sérialisés + mappés depuis Firestore par champ).
-keep class dj.velox.client.domain.model.** { *; }
-keepclassmembers enum dj.velox.client.** { *; }

# ── Hilt / Dagger ────────────────────────────────────────────
# Hilt fournit déjà ses propres règles consumer (EntryPoint, allowobfuscation/allowshrinking) :
# un -keep large sur dagger.hilt.** neutralisait le shrink/optimize R8 sur tout son runtime
# (recommandation Play Console « R8 optimisation »). On garde juste javax.inject (annotations,
# coût nul) et les avertissements de compilation.
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ── Firebase / Google ────────────────────────────────────────
# Les artefacts Firebase/Play Services embarquent leurs propres proguard.txt consumer
# (SafeParcelable, CREATOR, etc., appliqués automatiquement par AGP) : un -keep large ici
# empêchait R8 de réduire/optimiser tout ce code, alors qu'il n'est pas nécessaire.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Credential Manager + Google ID (connexion Google)
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ── MapLibre (cartes) ────────────────────────────────────────
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# ── Coil (images) ────────────────────────────────────────────
-dontwarn coil.**

# Évite les avertissements sur les annotations optionnelles.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
