package dj.velox.client.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit les singletons Firebase au graphe Hilt.
 * Backend PARTAGÉ avec l'app Flutter iOS — mêmes collections Firestore.
 *
 * NB : Firebase BOM 34+ fusionne les extensions KTX dans les artefacts
 * principaux → imports `com.google.firebase.<x>.<x>` (sans `.ktx`).
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = Firebase.storage

    // Région explicite us-central1 — identique à l'app Flutter
    // (`FirebaseFunctions.instanceFor(region: 'us-central1')`). Les Cloud Functions
    // (sendRestaurantNotification, etc.) y sont déployées.
    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = Firebase.functions("us-central1")

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = Firebase.messaging
}
