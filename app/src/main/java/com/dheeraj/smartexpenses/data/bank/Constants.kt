package com.dheeraj.smartexpenses.data.bank

/**
 * Constants used for bank message parsing.
 */
object Constants {
    
    object Parsing {
        const val AMOUNT_SCALE = 2
        const val MIN_MERCHANT_NAME_LENGTH = 2
        const val MD5_ALGORITHM = "MD5"
    }
    
    object TransactionTypes {
        const val CREDIT = "CREDIT"
        const val DEBIT = "DEBIT"
        const val TRANSFER = "TRANSFER"
    }
    
    object Channels {
        const val UPI = "UPI"
        const val IMPS = "IMPS"
        const val NEFT = "NEFT"
        const val RTGS = "RTGS"
        const val CARD = "CARD"
        const val ATM = "ATM"
        const val POS = "POS"
        const val NETBANKING = "NETBANKING"
    }
}
