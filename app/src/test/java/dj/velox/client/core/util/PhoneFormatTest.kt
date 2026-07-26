package dj.velox.client.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatTest {

    @Test
    fun `numero local prefixe avec l'indicatif Djibouti`() {
        assertEquals("+25377591823", toE164("77591823"))
    }

    @Test
    fun `espaces et separateurs ignores`() {
        assertEquals("+25377591823", toE164("77 59 18 23"))
        assertEquals("+25377591823", toE164("77-59-18-23"))
        assertEquals("+25377591823", toE164(" +253 77 59 18 23 "))
    }

    @Test
    fun `indicatif sans plus complete`() {
        assertEquals("+25377591823", toE164("25377591823"))
    }

    @Test
    fun `prefixe international 00 converti en plus`() {
        assertEquals("+25377591823", toE164("0025377591823"))
    }

    @Test
    fun `zero de tete retire avant l'indicatif`() {
        assertEquals("+25377591823", toE164("077591823"))
    }

    /** Un client étranger doit pouvoir garder sa ligne. */
    @Test
    fun `numero international conserve`() {
        assertEquals("+33612345678", toE164("+33 6 12 34 56 78"))
    }

    /**
     * Le cœur du correctif : deux saisies du même numéro doivent produire une identité
     * unique, sinon Firebase Auth crée deux comptes distincts.
     */
    @Test
    fun `formes equivalentes convergent vers la meme valeur`() {
        val formes = listOf("77591823", "+25377591823", "253 77591823", "0025377591823", "77 59 18 23")
        assertEquals(1, formes.map(::toE164).distinct().size)
    }

    @Test
    fun `chaine vide reste vide`() {
        assertEquals("", toE164("   "))
    }
}
