package dj.velox.client.core

/** Constantes métier (miroir constants.dart). */
object Constants {
    /** Frais de livraison fixes (FDJ). */
    const val DELIVERY_FEE = 500

    /** 1 point fidélité = kPointValue FDJ de réduction sur les frais de livraison. */
    const val POINT_VALUE = 15

    /** Moyens de paiement supportés. */
    val PAYMENT_METHODS = listOf("cash", "waafi", "d_money", "cac_pay")
}
