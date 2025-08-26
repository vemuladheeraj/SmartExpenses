package com.dheeraj.smartexpenses.data.bank

/**
 * Compiled regex patterns for bank message parsing.
 * This centralizes all the regex patterns used across different bank parsers.
 */
object CompiledPatterns {
    
    object Amount {
        val ALL_PATTERNS = listOf(
            Regex("""(?i)Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)₹\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)INR\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Amount\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Debited\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Credited\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Spent\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Received\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)""")
        )
    }
    
    object Merchant {
        val ALL_PATTERNS = listOf(
            Regex("""(?i)at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)(?:\s+by\s+|\s+on\s+|$)"""),
            Regex("""(?i)to\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)(?:\s+by\s+|\s+on\s+|$)"""),
            Regex("""(?i)from\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)(?:\s+by\s+|\s+on\s+|$)"""),
            Regex("""(?i)Info:\s*([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)"""),
            Regex("""(?i)Merchant:\s*([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)"""),
            Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)"""),
            Regex("""(?i)VPA\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        )
    }
    
    object Reference {
        val ALL_PATTERNS = listOf(
            Regex("""(?i)Ref\s*:?\s*([A-Z0-9]+)"""),
            Regex("""(?i)UTR\s*:?\s*([A-Z0-9]+)"""),
            Regex("""(?i)Transaction\s+ID\s*:?\s*([A-Z0-9]+)"""),
            Regex("""(?i)Reference\s*:?\s*([A-Z0-9]+)"""),
            Regex("""(?i)Auth\s+Code\s*:?\s*([A-Z0-9]+)""")
        )
    }
    
    object Account {
        val ALL_PATTERNS = listOf(
            Regex("""(?i)Account\s*:?\s*[X*]+\s*(\d{4})"""),
            Regex("""(?i)A/c\s*:?\s*[X*]+\s*(\d{4})"""),
            Regex("""(?i)Card\s*:?\s*[X*]+\s*(\d{4})"""),
            Regex("""(?i)ending\s+with\s*(\d{4})"""),
            Regex("""(?i)XX+(\d{4})""")
        )
    }
    
    object Balance {
        val ALL_PATTERNS = listOf(
            Regex("""(?i)Balance\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Avl\s+Bal\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Available\s+Balance\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Current\s+Balance\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)"""),
            Regex("""(?i)Total\s+Balance\s*:?\s*Rs\.?\s*([\d,]+(?:\.\d{2})?)""")
        )
    }
    
    object Cleaning {
        val TRAILING_PARENTHESES = Regex("""\s*\([^)]*\)\s*$""")
        val REF_NUMBER_SUFFIX = Regex("""\s*Ref\s*:?\s*[A-Z0-9]+\s*$""")
        val DATE_SUFFIX = Regex("""\s*\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\s*$""")
        val UPI_SUFFIX = Regex("""\s*UPI\s*$""")
        val TIME_SUFFIX = Regex("""\s*\d{1,2}:\d{2}\s*(?:AM|PM)?\s*$""")
        val TRAILING_DASH = Regex("""\s*-\s*$""")
        val PVT_LTD = Regex("""\s*(?:PVT|PRIVATE)\s+LTD\s*$""")
        val LTD = Regex("""\s*LTD\s*$""")
    }
    
    object HDFC {
        val DLT_PATTERNS = listOf(
            Regex("""HDFC[A-Z0-9]{6,}"""),
            Regex("""HDFCB[A-Z0-9]{5,}""")
        )
        
        val SALARY_PATTERN = Regex("""(?i)for\s+[^-]+-[^-]+-[^-]+-[^-]+-([^-]+)""")
        val SIMPLE_SALARY_PATTERN = Regex("""(?i)SALARY-([^-]+)""")
        val INFO_PATTERN = Regex("""(?i)Info:\s*([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        val VPA_WITH_NAME = Regex("""(?i)VPA\s+[^@]+@[^\s]+\s*\(([^)]+)\)""")
        val UPI_MERCHANT = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        
        // Additional patterns needed for comprehensive parsing
        val REF_SIMPLE = Regex("""(?i)Ref\s*:?\s*([A-Z0-9]+)""")
        val UPI_REF_NO = Regex("""(?i)UPI\s+Ref\s*:?\s*([A-Z0-9]+)""")
        val REF_NO = Regex("""(?i)Ref\s+No\s*:?\s*([A-Z0-9]+)""")
        val REF_END = Regex("""(?i)Ref\s*:?\s*([A-Z0-9]+)\s*$""")
        
        val ACCOUNT_DEPOSITED = Regex("""(?i)Account\s+[X*]+\s*(\d{4})""")
        val ACCOUNT_FROM = Regex("""(?i)from\s+[X*]+\s*(\d{4})""")
        val ACCOUNT_SIMPLE = Regex("""(?i)A/c\s+[X*]+\s*(\d{4})""")
        val ACCOUNT_GENERIC = Regex("""(?i)XX+(\d{4})""")
        
        val AMOUNT_WILL_DEDUCT = Regex("""(?i)will\s+be\s+deducted\s*:?\s*INR\.?\s*([0-9,]+(?:\.\d{2})?)""")
        val DEDUCTION_DATE = Regex("""(?i)on\s+(\d{2}/\d{2}/\d{4})""")
        val MANDATE_MERCHANT = Regex("""(?i)for\s+([^.\n]+?)(?:\s+on\s+|\s+UMN|$)""")
        val UMN_PATTERN = Regex("""(?i)UMN\s*:?\s*([A-Z0-9]+)""")
    }
    
    object ICICI {
        val DLT_PATTERNS = listOf(
            Regex("""ICICI[A-Z0-9]{6,}"""),
            Regex("""ICICIB[A-Z0-9]{5,}""")
        )
        
        val CARD_PATTERN = Regex("""(?i)Card\s+[X*]+\s*(\d{4})""")
        val UPI_PATTERN = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        val MERCHANT_PATTERN = Regex("""(?i)at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
    }
    
    object SBI {
        val DLT_PATTERNS = listOf(
            Regex("""SBI[A-Z0-9]{6,}"""),
            Regex("""SBIN[A-Z0-9]{5,}""")
        )
        
        val ACCOUNT_PATTERN = Regex("""(?i)A/c\s+[X*]+\s*(\d{4})""")
        val UPI_PATTERN = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        val MERCHANT_PATTERN = Regex("""(?i)to\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
    }
    
    object Axis {
        val DLT_PATTERNS = listOf(
            Regex("""AXIS[A-Z0-9]{6,}"""),
            Regex("""AXISB[A-Z0-9]{5,}""")
        )
        
        val CARD_PATTERN = Regex("""(?i)Card\s+[X*]+\s*(\d{4})""")
        val UPI_PATTERN = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
        val MERCHANT_PATTERN = Regex("""(?i)at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
    }
}
