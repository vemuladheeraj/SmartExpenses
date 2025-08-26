package com.dheeraj.smartexpenses.data.bank

import android.util.Log

/**
 * Factory for creating bank-specific parsers based on SMS sender.
 */
object BankParserFactory {
    
    private val parsers = listOf(
        HDFCBankParser(),
        ICICIBankParser(),
        SBIBankParser(),
        AxisBankParser(),
        KotakBankParser(),
        IndianBankParser(),
        FederalBankParser(),
        CanaraBankParser(),
        BankOfBarodaParser(),
        PNBBankParser(),
        UnionBankParser(),
        CentralBankOfIndiaParser(),
        // UCOBankParser(), // This parser doesn't exist
        IDBIBankParser(),
        JupiterBankParser(),
        IDFCFirstBankParser(),
        JioPaymentsBankParser(),
        UtkarshBankParser(),
        KarnatakaBankParser(),
        SliceParser(),
        JuspayParser(),
        HSBCBankParser()
    )
    
    /**
     * Returns the appropriate bank parser for the given sender.
     * Returns null if no specific parser is found.
     */
    fun getParser(sender: String): BankParser? {
        Log.d("BankParserFactory", "Looking for parser for sender: $sender")
        
        for (parser in parsers) {
            try {
                if (parser.canHandle(sender)) {
                    Log.d("BankParserFactory", "Found matching parser: ${parser.javaClass.simpleName}")
                    return parser
                }
            } catch (e: Exception) {
                Log.e("BankParserFactory", "Error checking parser ${parser.javaClass.simpleName}", e)
            }
        }
        
        Log.d("BankParserFactory", "No matching parser found for sender: $sender")
        return null
    }
    
    /**
     * Returns all available bank parsers.
     */
    fun getAllParsers(): List<BankParser> = parsers
    
    /**
     * Checks if the sender belongs to any known bank.
     */
    fun isKnownBankSender(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }
}