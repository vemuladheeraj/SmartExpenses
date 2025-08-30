# SmartExpenses - Enhanced SMS Parsing & Transfer Detection

## 🚀 Recent Major Enhancements (Latest Update)

### **Enhanced SMS Parsing with Spam Protection & Transfer Detection**

The SmartExpenses app has undergone comprehensive improvements to address potential issues with wrong amount detection, spam message handling, and transfer detection accuracy.

#### **🛡️ New Spam Detection System**
- **Multi-layered spam filtering** to block promotional and marketing messages
- **Pattern-based detection** for cashback, rewards, offers, and promotional content
- **Early rejection** of spam messages before parsing to improve performance

#### **🔍 Enhanced Amount Validation**
- **Context-aware amount extraction** that prevents false positives from balance alerts
- **Balance/limit message filtering** to avoid processing informational amounts
- **Promotional amount detection** for small amounts like ₹1, ₹5, ₹10
- **Range validation** with minimum (₹10) and maximum (₹10 crore) limits

#### **🔄 Improved Transfer Detection**
- **Enhanced internal transfer detection** for same-amount DEBIT/CREDIT pairs
- **Self-transfer keyword detection** for account-to-account movements
- **Account transfer pattern recognition** for multi-account transfers
- **5-minute time window** for transfer detection (increased from 3 minutes)

#### **💳 Corrected Credit Card Logic**
- **Credit card bill payments** now correctly classified as **DEBIT (expenses)**
- **No longer misclassified** as TRANSFER (debt settlement)
- **Proper expense categorization** for all credit card related transactions

## 📱 App Overview

SmartExpenses is an Android app that automatically tracks your income and expenses by parsing SMS messages from Indian banks. It provides real-time financial insights without requiring manual entry.

## ✨ Key Features

### **🔐 Privacy-First Design**
- **100% offline processing** - No SMS content sent to external servers
- **Local storage only** - All data stays on your device
- **No internet permission required** - Works completely offline

### **📊 Smart Transaction Detection**
- **Automatic categorization** of income (CREDIT) vs expenses (DEBIT)
- **Transfer detection** to avoid double-counting internal movements
- **Merchant extraction** from SMS content
- **Payment channel detection** (UPI, IMPS, NEFT, RTGS, POS, ATM, CARD)

### **🎯 Enhanced Accuracy**
- **Spam message filtering** to prevent false transactions
- **Context-aware parsing** for better amount extraction
- **Multi-scenario transfer detection** for accurate financial reporting
- **Bank-specific optimizations** for Indian banking SMS formats

### **📈 Real-time Insights**
- **Live balance tracking** with income/expense breakdown
- **Monthly summaries** and trend analysis
- **Quick statistics** and transaction history
- **Search and filtering** capabilities

## 🏦 Supported Banks & Payment Systems

### **Major Private Banks**
- HDFC Bank, ICICI Bank, Axis Bank, Kotak Bank
- Yes Bank, IDFC Bank, IndusInd Bank, RBL Bank
- Federal Bank, Karnataka Bank, South Indian Bank

### **Public Sector Banks**
- State Bank of India, Punjab National Bank, Canara Bank
- Bank of Baroda, Union Bank, Bank of India
- Central Bank, UCO Bank, Indian Bank

### **Payment Systems**
- UPI (Google Pay, PhonePe, Paytm, BHIM)
- IMPS, NEFT, RTGS
- Credit/Debit Cards, POS, ATM

## 🔧 Technical Architecture

### **Enhanced SMS Pipeline**
1. **Spam Detection** - Multi-layered filtering before parsing
2. **Amount Validation** - Context-aware extraction with balance filtering
3. **Transaction Classification** - CREDIT/DEBIT/TRANSFER determination
4. **Transfer Detection** - Enhanced logic for internal movements
5. **Data Storage** - Local SQLite database with Room ORM

### **Performance Optimizations**
- **Early rejection** of spam and invalid messages
- **Memory-efficient** transfer detection with bounded lists
- **Optimized regex patterns** for Indian banking SMS
- **Background processing** with progress tracking

## 📋 Installation & Setup

### **Requirements**
- Android 6.0 (API 23) or higher
- SMS read permission
- No internet connection required

### **Setup Steps**
1. **Install the app** from APK or build from source
2. **Grant SMS permissions** when prompted
3. **Automatic import** of recent SMS messages
4. **Start tracking** your finances immediately

## 🧪 Testing & Validation

### **Comprehensive Test Coverage**
- **Spam detection tests** for promotional messages
- **Amount validation tests** for balance alerts
- **Transfer detection tests** for internal movements
- **Credit card logic tests** for proper categorization

### **Test Cases Included**
- See `test_enhanced_parsing.md` for detailed test scenarios
- Covers all major Indian bank SMS formats
- Includes edge cases and error conditions

## 📚 Documentation

### **Technical Guides**
- `ENHANCED_SMS_PARSING_GUIDE.md` - Comprehensive implementation details
- `INDIAN_BANK_SMS_REGEX_GUIDE.md` - Regex patterns and examples
- `CREDIT_CARD_BILL_PAYMENT_GUIDE.md` - Credit card handling logic
- `NEFT_REFERENCE_DETECTION_FIX.md` - NEFT transaction improvements

### **User Guides**
- `Workflow.md` - App workflow and user experience
- `CRASH_PREVENTION_GUIDE.md` - Error handling and stability
- `MIGRATION_SUMMARY.md` - Database migration details

## 🔒 Privacy & Security

### **Data Protection**
- **No cloud storage** - Everything stays on your device
- **No external APIs** - Complete offline functionality
- **No data sharing** - Your financial data is private
- **Local encryption** - Database protection on device

### **Permission Usage**
- **READ_SMS** - Required for transaction detection
- **RECEIVE_SMS** - Required for real-time updates
- **No internet permission** - Cannot send data anywhere

## 🚀 Performance & Reliability

### **Optimizations**
- **Regex-only parsing** - No AI dependencies or external calls
- **Efficient memory usage** - Bounded lists and cleanup
- **Background processing** - Non-blocking UI operations
- **Error resilience** - Graceful handling of parsing failures

### **Stability Features**
- **Crash prevention** with comprehensive error handling
- **Database fallbacks** for migration failures
- **Progress tracking** for long operations
- **Logging and debugging** capabilities

## 🤝 Contributing

### **Development Setup**
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on device/emulator

### **Testing**
- Run the comprehensive test suite
- Test with real Indian bank SMS messages
- Verify spam detection and transfer logic
- Check performance with large SMS volumes

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- **Indian banking community** for SMS format insights
- **Android development community** for best practices
- **Privacy advocates** for offline-first design principles

## 📞 Support

For issues, questions, or contributions:
- Check the comprehensive documentation
- Review the test cases and examples
- Test with your specific bank's SMS format
- Report bugs with detailed SMS examples

---

**SmartExpenses** - Your personal finance tracker with enhanced accuracy and privacy protection. 🚀💰


