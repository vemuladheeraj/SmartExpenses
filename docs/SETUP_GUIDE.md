# SmartExpenses - Setup & Installation Guide

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation Methods](#installation-methods)
3. [Initial Setup](#initial-setup)
4. [Configuration](#configuration)
5. [Troubleshooting](#troubleshooting)
6. [Advanced Setup](#advanced-setup)
7. [Development Setup](#development-setup)

## 🔧 Prerequisites

### System Requirements

#### **Android Device Requirements**
- **Android Version**: 6.0 (API 23) or higher
- **RAM**: Minimum 2GB, Recommended 4GB+
- **Storage**: At least 100MB free space
- **Permissions**: SMS read and receive permissions
- **Internet**: Optional (only for AI features)

#### **Supported Android Versions**
| Android Version | API Level | Support Status |
|----------------|-----------|----------------|
| Android 6.0 | API 23 | ✅ Supported |
| Android 7.0 | API 24 | ✅ Supported |
| Android 8.0 | API 26 | ✅ Supported |
| Android 9.0 | API 28 | ✅ Supported |
| Android 10 | API 29 | ✅ Supported |
| Android 11 | API 30 | ✅ Supported |
| Android 12 | API 31 | ✅ Supported |
| Android 13 | API 33 | ✅ Supported |
| Android 14 | API 34 | ✅ Supported |

#### **Device Compatibility**
- **Smartphones**: All Android smartphones
- **Tablets**: Android tablets (with SMS capability)
- **Emulators**: Android emulators for development
- **Rooted Devices**: Supported with additional security considerations

### Required Permissions

#### **Essential Permissions**
```xml
<!-- SMS Processing -->
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />

<!-- Biometric Authentication -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />

<!-- Internet (for AI features only) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

#### **Permission Descriptions**
- **READ_SMS**: Required to read SMS messages for transaction extraction
- **RECEIVE_SMS**: Required for real-time SMS processing
- **USE_BIOMETRIC**: Required for fingerprint and face unlock
- **USE_FINGERPRINT**: Required for fingerprint authentication
- **INTERNET**: Required only for AI features (optional)
- **ACCESS_NETWORK_STATE**: Required for network status checking
- **POST_NOTIFICATIONS**: Required for budget alerts and notifications

## 📱 Installation Methods

### Method 1: Download APK (Recommended)

#### **Step 1: Download the APK**
1. Visit the [Releases](https://github.com/yourusername/SmartExpenses/releases) page
2. Download the latest APK file (`SmartExpenses-v1.0.0.apk`)
3. Save the file to your device's Downloads folder

#### **Step 2: Enable Unknown Sources**
1. Open **Settings** on your Android device
2. Navigate to **Security** or **Privacy & Security**
3. Find **Install unknown apps** or **Unknown sources**
4. Enable installation from your browser or file manager

#### **Step 3: Install the APK**
1. Open your **File Manager** or **Downloads** app
2. Locate the downloaded APK file
3. Tap on the APK file
4. Tap **Install** when prompted
5. Wait for installation to complete
6. Tap **Open** to launch the app

### Method 2: Build from Source

#### **Prerequisites for Building**
- **Android Studio**: Latest version (Hedgehog or newer)
- **JDK**: Java Development Kit 17 or higher
- **Git**: Version control system
- **Android SDK**: API 23+ and build tools

#### **Step 1: Clone the Repository**
```bash
git clone https://github.com/yourusername/SmartExpenses.git
cd SmartExpenses
```

#### **Step 2: Open in Android Studio**
1. Launch **Android Studio**
2. Select **Open an existing project**
3. Navigate to the cloned repository folder
4. Click **OK** to open the project

#### **Step 3: Sync Dependencies**
1. Android Studio will automatically sync Gradle dependencies
2. Wait for the sync to complete
3. Resolve any dependency conflicts if prompted

#### **Step 4: Build the Project**
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

#### **Step 5: Install on Device**
1. Connect your Android device via USB
2. Enable **USB Debugging** in Developer Options
3. Run the app from Android Studio or install the generated APK

### Method 3: Google Play Store (Future)

*Note: The app is not yet available on Google Play Store. This section will be updated when it becomes available.*

## 🚀 Initial Setup

### First Launch Setup

#### **Step 1: Launch the App**
1. Find **SmartExpenses** in your app drawer
2. Tap to open the app
3. The app will show a welcome screen

#### **Step 2: Grant Permissions**
1. The app will request SMS permissions
2. Tap **Allow** to grant READ_SMS permission
3. Tap **Allow** to grant RECEIVE_SMS permission
4. For Android 13+, also grant POST_NOTIFICATIONS permission

#### **Step 3: Automatic SMS Import**
1. The app will automatically start importing recent SMS messages
2. A progress dialog will show the import status
3. Wait for the import to complete (may take a few minutes)

#### **Step 4: Review Imported Transactions**
1. Navigate to the **Home** tab
2. Review the imported transactions
3. Check if amounts and categories are correct
4. Make manual adjustments if needed

### Basic Configuration

#### **Step 1: Set Up Budgets**
1. Navigate to the **Budget** tab
2. Tap **Add Budget** for each spending category
3. Set monthly limits for:
   - Food & Dining
   - Transportation
   - Shopping
   - Entertainment
   - Bills & Utilities
   - Other expenses

#### **Step 2: Configure Notifications**
1. Go to **Settings** → **Notifications**
2. Enable budget alerts
3. Set notification preferences
4. Configure alert thresholds

#### **Step 3: Set Up Security (Optional)**
1. Go to **Settings** → **Security**
2. Enable **Biometric Lock** if your device supports it
3. Enable **App Lock** for additional security
4. Test the authentication setup

## ⚙️ Configuration

### SMS Processing Configuration

#### **Import Settings**
1. Go to **Settings** → **SMS & Data Management**
2. Configure import range:
   - **Last 3 months** (default)
   - **Last 6 months**
   - **Last 12 months**
   - **All time**

#### **Bank Selection**
1. In **Settings** → **SMS & Data Management**
2. Select which banks to process:
   - ✅ HDFC Bank
   - ✅ ICICI Bank
   - ✅ Axis Bank
   - ✅ SBI
   - ✅ Other banks

#### **Spam Filtering**
1. Go to **Settings** → **Advanced**
2. Configure spam detection sensitivity:
   - **High**: Strict filtering (recommended)
   - **Medium**: Balanced filtering
   - **Low**: Minimal filtering

### AI Features Configuration

#### **Google AI Studio Setup**
1. Navigate to **AI Insights** tab
2. Tap **Setup AI Insights**
3. Follow the setup wizard:
   - Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
   - Sign in with your Google account
   - Create a new API key
   - Copy the API key
   - Paste it in the app

#### **AI Configuration Options**
1. **API Key**: Your Google AI Studio API key
2. **Custom Endpoint**: Alternative AI service endpoint
3. **Request Timeout**: API request timeout (default: 30 seconds)
4. **Cache Duration**: How long to cache AI responses

#### **Privacy Settings**
1. **Data Sharing**: Control what data is sent to AI
2. **Analytics**: Enable/disable usage analytics
3. **Crash Reporting**: Enable/disable crash reports

### Security Configuration

#### **Biometric Authentication**
1. Go to **Settings** → **Security**
2. Enable **Biometric Lock**
3. Test fingerprint/face recognition
4. Set up fallback authentication

#### **App Lock Settings**
1. Enable **App Lock**
2. Configure lock timeout:
   - **Immediate**: Lock when app is minimized
   - **1 minute**: Lock after 1 minute of inactivity
   - **5 minutes**: Lock after 5 minutes of inactivity
   - **Never**: Disable auto-lock

#### **Data Encryption**
1. Verify **Encrypted Storage** is enabled
2. Check **Secure Preferences** status
3. Review **Privacy Settings**

### Export Configuration

#### **Export Settings**
1. Go to **Settings** → **Export & Backup**
2. Configure export formats:
   - **CSV Format**: Comma-separated values
   - **PDF Format**: Formatted reports
3. Set export preferences:
   - **Date Range**: All time or custom range
   - **Include Categories**: Export category information
   - **Include Raw SMS**: Export original SMS text

#### **Backup Settings**
1. Enable **Automatic Backup**
2. Set backup frequency:
   - **Daily**: Backup every day
   - **Weekly**: Backup every week
   - **Monthly**: Backup every month
3. Configure backup location

## 🔧 Troubleshooting

### Common Issues

#### **SMS Not Importing**
**Problem**: SMS messages are not being imported or processed.

**Solutions**:
1. **Check Permissions**:
   - Go to Settings → Apps → SmartExpenses → Permissions
   - Ensure SMS permissions are granted
   - Re-grant permissions if needed

2. **Check SMS Format**:
   - Verify your bank's SMS format is supported
   - Check if SMS contains transaction information
   - Look for amount and transaction type keywords

3. **Manual Import**:
   - Go to Settings → SMS & Data Management
   - Tap "Re-import SMS"
   - Wait for processing to complete

4. **Check Spam Filtering**:
   - Go to Settings → Advanced
   - Adjust spam detection sensitivity
   - Try disabling spam filtering temporarily

#### **AI Features Not Working**
**Problem**: AI insights and chat are not functioning.

**Solutions**:
1. **Check API Key**:
   - Verify API key is correctly entered
   - Ensure API key starts with "AIza"
   - Check if API key has expired

2. **Check Internet Connection**:
   - Ensure device has internet connectivity
   - Check if AI service is accessible
   - Try using mobile data if WiFi fails

3. **Check API Limits**:
   - Verify Google AI Studio quota
   - Check if daily limits are exceeded
   - Wait for quota reset if needed

4. **Clear Cache**:
   - Go to Settings → AI Configuration
   - Tap "Clear AI Cache"
   - Restart the app

#### **Budget Alerts Not Working**
**Problem**: Budget notifications are not being received.

**Solutions**:
1. **Check Notification Permissions**:
   - Go to Settings → Apps → SmartExpenses → Notifications
   - Ensure notifications are enabled
   - Check notification categories

2. **Check Budget Settings**:
   - Verify budgets are set correctly
   - Check if budget alerts are enabled
   - Ensure spending is being tracked

3. **Check Notification Channels**:
   - Go to Settings → Notifications
   - Verify budget alert channel is enabled
   - Check notification importance level

4. **Test Notifications**:
   - Go to Settings → Notifications
   - Tap "Test Notification"
   - Check if notification appears

#### **App Crashes or Freezes**
**Problem**: App crashes or becomes unresponsive.

**Solutions**:
1. **Restart the App**:
   - Force close the app
   - Clear app from recent apps
   - Restart the app

2. **Clear App Data**:
   - Go to Settings → Apps → SmartExpenses → Storage
   - Tap "Clear Data"
   - Re-configure the app

3. **Check Device Storage**:
   - Ensure sufficient storage space
   - Clear unnecessary files
   - Free up at least 1GB of space

4. **Update the App**:
   - Check for app updates
   - Install latest version
   - Restart device after update

#### **Data Export Issues**
**Problem**: CSV or PDF export is not working.

**Solutions**:
1. **Check Storage Permissions**:
   - Ensure storage permissions are granted
   - Check if external storage is available
   - Verify file system access

2. **Check File Format**:
   - Ensure correct file format is selected
   - Check if file extension is correct
   - Verify file size limits

3. **Check Sharing Options**:
   - Ensure sharing app is installed
   - Check if file sharing is enabled
   - Try different sharing methods

4. **Clear Export Cache**:
   - Go to Settings → Export & Backup
   - Clear export cache
   - Try export again

### Performance Issues

#### **Slow SMS Processing**
**Problem**: SMS import is taking too long.

**Solutions**:
1. **Reduce Import Range**:
   - Import only last 3 months instead of all time
   - Process SMS in smaller batches
   - Use background processing

2. **Optimize Device Performance**:
   - Close other apps
   - Free up RAM
   - Restart device

3. **Check SMS Volume**:
   - Large number of SMS messages take longer
   - Consider filtering non-transactional SMS
   - Use incremental import

#### **High Memory Usage**
**Problem**: App is using too much memory.

**Solutions**:
1. **Clear Cache**:
   - Go to Settings → Storage
   - Clear app cache
   - Clear AI insights cache

2. **Limit Data Range**:
   - Reduce transaction history range
   - Clear old transaction data
   - Use data pagination

3. **Optimize Settings**:
   - Disable unnecessary features
   - Reduce notification frequency
   - Limit background processing

### Data Issues

#### **Incorrect Transaction Amounts**
**Problem**: Transaction amounts are not being parsed correctly.

**Solutions**:
1. **Check SMS Format**:
   - Verify bank SMS format
   - Check for amount patterns
   - Look for currency symbols

2. **Manual Correction**:
   - Edit transaction amounts manually
   - Use transaction editing feature
   - Report parsing issues

3. **Update Parser**:
   - Check for app updates
   - Update SMS parsing rules
   - Contact support for new bank formats

#### **Missing Transactions**
**Problem**: Some transactions are not being imported.

**Solutions**:
1. **Check SMS Filtering**:
   - Verify spam filtering settings
   - Check if SMS is being filtered out
   - Adjust filtering sensitivity

2. **Manual Import**:
   - Add missing transactions manually
   - Use manual transaction entry
   - Import from other sources

3. **Check SMS Storage**:
   - Ensure SMS messages are stored
   - Check SMS app settings
   - Verify SMS backup

## 🔧 Advanced Setup

### Developer Options

#### **Enable Developer Mode**
1. Go to **Settings** → **About Phone**
2. Tap **Build Number** 7 times
3. Enter device PIN/pattern if prompted
4. Developer options will be enabled

#### **USB Debugging**
1. Go to **Settings** → **Developer Options**
2. Enable **USB Debugging**
3. Connect device to computer
4. Allow USB debugging when prompted

#### **Advanced Settings**
1. **Stay Awake**: Keep screen on while charging
2. **USB Debugging**: Enable for development
3. **Mock Locations**: For testing location features
4. **Don't Keep Activities**: For memory testing

### Root Access (Advanced Users)

#### **Root Requirements**
- **Rooted Device**: Device with root access
- **Root Manager**: SuperSU, Magisk, or similar
- **Backup**: Complete device backup before proceeding

#### **Root Benefits**
- **Advanced Permissions**: Enhanced SMS access
- **System Integration**: Deeper system integration
- **Custom Modifications**: App modifications
- **Performance Tuning**: Advanced performance options

#### **Root Risks**
- **Security**: Increased security risks
- **Warranty**: May void device warranty
- **Stability**: Potential system instability
- **Updates**: May affect system updates

### Custom ROM Support

#### **Supported ROMs**
- **LineageOS**: Full compatibility
- **Pixel Experience**: Full compatibility
- **AOSP**: Full compatibility
- **Custom ROMs**: Varies by ROM

#### **Installation on Custom ROMs**
1. **Check Compatibility**: Verify ROM compatibility
2. **Install GApps**: Install Google Apps if needed
3. **Grant Permissions**: Grant all required permissions
4. **Test Features**: Test all app features

### Enterprise Deployment

#### **MDM Integration**
1. **Mobile Device Management**: Deploy via MDM
2. **Policy Configuration**: Set enterprise policies
3. **Security Compliance**: Ensure security compliance
4. **User Management**: Manage user access

#### **Enterprise Features**
- **Centralized Management**: Central app management
- **Policy Enforcement**: Enforce security policies
- **Data Protection**: Enhanced data protection
- **Compliance**: Regulatory compliance

## 💻 Development Setup

### Prerequisites for Development

#### **Development Tools**
- **Android Studio**: Latest version
- **JDK**: Java Development Kit 17+
- **Git**: Version control system
- **Android SDK**: API 23+ and build tools

#### **Development Environment**
```bash
# Check Java version
java -version

# Check Android SDK
echo $ANDROID_HOME

# Check Git version
git --version
```

### Project Setup

#### **Clone Repository**
```bash
git clone https://github.com/yourusername/SmartExpenses.git
cd SmartExpenses
```

#### **Open in Android Studio**
1. Launch Android Studio
2. Select "Open an existing project"
3. Navigate to the cloned repository
4. Click "OK" to open

#### **Sync Dependencies**
```bash
# Sync Gradle dependencies
./gradlew build

# Or use Android Studio
# File → Sync Project with Gradle Files
```

### Build Configuration

#### **Build Types**
```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

#### **Build Commands**
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Run lint
./gradlew lint
```

### Testing Setup

#### **Unit Tests**
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

#### **Integration Tests**
```bash
# Run integration tests
./gradlew connectedAndroidTest

# Run specific test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dheeraj.smartexpenses.SmsParsingIntegrationTest
```

#### **UI Tests**
```bash
# Run UI tests
./gradlew connectedAndroidTest

# Run on specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dheeraj.smartexpenses.UiTest
```

### Debugging

#### **Logcat Configuration**
1. Open **Logcat** in Android Studio
2. Set filter to **SmartExpenses**
3. Configure log levels:
   - **Verbose**: All logs
   - **Debug**: Debug information
   - **Info**: General information
   - **Warning**: Warnings
   - **Error**: Errors only

#### **Debug Build**
```bash
# Install debug build
./gradlew installDebug

# Run with debugger
# Use Android Studio debugger
```

#### **Performance Profiling**
1. **CPU Profiler**: Monitor CPU usage
2. **Memory Profiler**: Monitor memory usage
3. **Network Profiler**: Monitor network activity
4. **Energy Profiler**: Monitor battery usage

### Contributing

#### **Code Style**
- Follow **Kotlin coding conventions**
- Use **Jetpack Compose best practices**
- Write **comprehensive unit tests**
- Document **public APIs**

#### **Pull Request Process**
1. **Fork** the repository
2. **Create** a feature branch
3. **Make** your changes
4. **Test** your changes
5. **Submit** a pull request

#### **Issue Reporting**
1. **Check** existing issues
2. **Create** a new issue
3. **Provide** detailed information
4. **Include** logs and screenshots

---

This comprehensive setup guide provides detailed instructions for installing, configuring, and troubleshooting the SmartExpenses application. It covers everything from basic installation to advanced development setup, ensuring users can successfully deploy and use the application in various environments.
