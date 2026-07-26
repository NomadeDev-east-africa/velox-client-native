package dj.velox.client.core.util

/** Indicatif de Djibouti, préfixé automatiquement aux numéros locaux. */
private const val DJ_DIAL_CODE = "+253"

/**
 * Met un numéro saisi au format E.164 attendu par Firebase Auth.
 *
 * Les clients tapent leur numéro comme ils le disent — `77 59 18 23`, `77591823`,
 * `25377591823`, `00253 77591823`. Firebase, lui, exige `+25377591823` : sans
 * normalisation, `verifyPhoneNumber` échoue, et surtout le **même** numéro saisi sous
 * deux formes produirait deux identités différentes.
 *
 * Un numéro déjà international (`+33…`) est conservé tel quel : l'app doit rester
 * utilisable par un client de passage avec une ligne étrangère.
 */
fun toE164(raw: String): String {
    val cleaned = raw.filterNot { it.isWhitespace() || it in "-().​" }
    return when {
        cleaned.isEmpty() -> cleaned
        cleaned.startsWith("+") -> cleaned
        cleaned.startsWith("00") -> "+${cleaned.drop(2)}"
        cleaned.startsWith("253") -> "+$cleaned"
        // Numéro local : on retire un éventuel 0 de tête avant de préfixer l'indicatif.
        else -> "$DJ_DIAL_CODE${cleaned.trimStart('0')}"
    }
}
