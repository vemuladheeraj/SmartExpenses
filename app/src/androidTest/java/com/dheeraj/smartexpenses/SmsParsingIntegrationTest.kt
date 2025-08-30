package com.dheeraj.smartexpenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.SmsParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsParsingIntegrationTest {
    
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SmsParser.init(context)
    }
    
    @Test
    fun testHDFCBankDebitSMS() {
        // Test HDFC Bank debit SMS
        val sender = "HDFCBANK"
        val body = "Rs.500.00 debited from A/c XX1234 on 15-12-2023 14:30:15 at ZOMATO. UPI Ref: 123456789. Avl Bal: Rs.25,000.00"
        val timestamp = System.currentTimeMillis()
        
        val result = SmsParser.parse(sender, body, timestamp)
        
        assertNotNull(result)
        assertEquals("DEBIT", result.type)
        assertEquals(50000L, result.amountMinor) // 500.00 in paise
        assertEquals("UPI", result.channel)
        assertEquals("ZOMATO", result.merchant)
        assertEquals("XX1234", result.accountTail)
        assertEquals("HDFC", result.bank)
    }
    
    @Test
    fun testSBIBankCreditSMS() {
        // Test SBI Bank credit SMS
        val sender = "SBIINB"
        val body = "Rs.10,000.00 credited to A/c XX5678 on 15-12-2023 10:15:30 by NEFT from A/C 1234567890. Avl Bal: Rs.50,000.00"
        val timestamp = System.currentTimeMillis()
        
        val result = SmsParser.parse(sender, body, timestamp)
        
        assertNotNull(result)
        assertEquals("CREDIT", result.type)
        assertEquals(1000000L, result.amountMinor) // 10,000.00 in paise
        assertEquals("NEFT", result.channel)
        assertNull(result.merchant) // NEFT transfers don't have merchants
        assertEquals("XX5678", result.accountTail)
        assertEquals("SBI", result.bank)
    }
    
    @Test
    fun testICICIBankCardTransaction() {
        // Test ICICI Bank card transaction
        val sender = "ICICIB"
        val body = "Rs.1,250.00 spent on ICICI Bank Credit Card XX4321 at AMAZON on 15-12-2023 16:45:20. Available Credit Limit: Rs.50,000.00"
        val timestamp = System.currentTimeMillis()
        
        val result = SmsParser.parse(sender, body, timestamp)
        
        assertNotNull(result)
        assertEquals("DEBIT", result.type)
        assertEquals(125000L, result.amountMinor) // 1,250.00 in paise
        assertEquals("CARD", result.channel)
        assertEquals("AMAZON", result.merchant)
        assertEquals("XX4321", result.accountTail)
        assertEquals("ICICI", result.bank)
    }
    
    @Test
    fun testTransferDetection() {
        // Test inter-account transfer detection
        val sender = "HDFCBANK"
        val body = "Rs.5,000.00 transferred from A/c XX1234 to A/c XX5678 on 15-12-2023 12:00:00. UPI Ref: 987654321. Avl Bal: Rs.20,000.00"
        val timestamp = System.currentTimeMillis()
        
        val result = SmsParser.parse(sender, body, timestamp)
        
        assertNotNull(result)
        // Should be detected as a transfer (not counted in regular spending)
        assertEquals("DEBIT", result.type) // Still DEBIT but should be filtered out in analytics
        assertEquals(500000L, result.amountMinor)
        assertEquals("UPI", result.channel)
        assertEquals("XX1234", result.accountTail)
        assertEquals("HDFC", result.bank)
    }
    
    @Test
    fun testInvalidSMS() {
        // Test invalid SMS that shouldn't be parsed
        val sender = "SPAM"
        val body = "Congratulations! You've won a prize. Click here to claim."
        val timestamp = System.currentTimeMillis()
        
        val result = SmsParser.parse(sender, body, timestamp)
        
        assertNull(result) // Should return null for invalid SMS
    }
    
    @Test
    fun testAmountParsingEdgeCases() {
        // Test various amount formats
        val testCases = listOf(
            "Rs.1,234.56" to 123456L,
            "Rs.1,00,000.00" to 10000000L,
            "Rs.500" to 50000L,
            "Rs.0.50" to 50L,
            "Rs.1,00,000.50" to 10000050L
        )
        
        testCases.forEach { (amountText, expectedPaise) ->
            val sender = "HDFCBANK"
            val body = "$amountText debited from A/c XX1234 on 15-12-2023 14:30:15 at TEST. UPI Ref: 123456789. Avl Bal: Rs.25,000.00"
            val timestamp = System.currentTimeMillis()
            
            val result = SmsParser.parse(sender, body, timestamp)
            
            assertNotNull("Failed to parse amount: $amountText", result)
            assertEquals("Amount mismatch for: $amountText", expectedPaise, result.amountMinor)
        }
    }
    
    @Test
    fun testMultipleBanks() {
        // Test parsing from different banks
        val bankTests = listOf(
            "HDFCBANK" to "HDFC",
            "SBIINB" to "SBI",
            "ICICIB" to "ICICI",
            "AXISB" to "AXIS",
            "KOTAK" to "KOTAK"
        )
        
        bankTests.forEach { (sender, expectedBank) ->
            val body = "Rs.100.00 debited from A/c XX1234 on 15-12-2023 14:30:15 at TEST. UPI Ref: 123456789. Avl Bal: Rs.25,000.00"
            val timestamp = System.currentTimeMillis()
            
            val result = SmsParser.parse(sender, body, timestamp)
            
            assertNotNull("Failed to parse bank: $sender", result)
            assertEquals("Bank mismatch for: $sender", expectedBank, result.bank)
        }
    }
}
