## SmartExpenses - App Workflow

### Overview
SmartExpenses is a Jetpack Compose Android app that extracts transactions from SMS using **regex-based parsing** and stores them in a Room database. The UI presents summaries and lists with filters. Manual transactions can also be added.

### App Startup
- Application class: `SmartExpensesApp`
  - Sets `SmartContextProvider.app` for global context access.
  - Initializes `SmsParser` for regex-based SMS parsing.
  - No AI models or TensorFlow dependencies.

- Entry Activity: `MainActivity`
  - Creates `HomeVm` via `by viewModels()`.
  - UI content set with `setContent { App(vm, requestPerms) }`.

### First-run permissions flow
Inside `App(vm, requestPerms)`:
- The app requests `READ_SMS` and `RECEIVE_SMS` permissions immediately (via a `LaunchedEffect`).
- On permission grant in `MainActivity` callback:
  - Calls `vm.importIfFirstRun()` to import recent SMS messages (default 6 months) exactly once.

### Navigation and Screens
Navigation root: `MainNavigation`
- Bottom tabs:
  - `Screen.Home` → `HomeScreen`
  - `Screen.Analytics` → `AnalyticsScreen`
  - `Screen.Settings` → `SettingsScreen`
- Additional route:
  - `Screen.TransactionList` → `TransactionListScreen`

Navigation behavior:
- Start destination is `home`.
- Bottom nav uses `popUpTo(startDestination)` with `saveState/restoreState` to preserve state when switching tabs.
- From `HomeScreen`, tapping "View All" navigates to `transaction_list`.

### Data Layer
- Room setup:
  - DB: `AppDb` (version 3), singleton via `AppDb.get(context)`.
  - Entity: `Transaction` with unique index on `(rawSender, rawBody, ts)` to prevent duplicate SMS-derived rows and indices on `ts` and `(type, ts)`.
  - DAO: `TxnDao` exposes reactive `Flow` queries for ranges, totals, and distinct `merchant`/`channel` lists, plus helpers like `exists(...)`, `clearSmsTransactions()`, and aggregate totals.
  - Migrations: 1→2 adds indexes and a unique constraint; 2→3 relies on `fallbackToDestructiveMigration` per comment due to amount storage changes.

### ViewModel: `HomeVm`
- Holds app state and orchestrates imports and summaries.
- Core state:
  - Time range selection: `RangeMode` (`CALENDAR_MONTH` or `ROLLING_MONTH`) derives a `(start,end)` range.
  - `items`: transactions in range (`Flow<List<Transaction>>`).
  - Totals in range: `totalDebit`, `totalCredit` and filtered versions excluding likely transfers for current month.
  - 6-month aggregates: `allItems`, `totalDebit6Months`, `totalCredit6Months` used by list screen summaries.
  - Import progress state: `_isImporting`, `_importProgress` for UI.

- First-run import logic:
  - `importIfFirstRun(monthsBack=6)`: checks shared pref `initial_import_done` and DB counts; triggers `importRecentSms()` when needed.
  - `importRecentSms(monthsBack)`: iterates SMS via `SmsImporter.importWithProgress`, deduplicates using `dao.exists(sender, body, ts)`, parses each SMS with `SmsParser.parse(...)`, and inserts resulting `Transaction`.
  - Marks import complete and persists `initial_import_done=true` on success.

- Transfer detection and summarization:
  - `isInterAccountTransfer(t)`: flags strong indicators from the SMS body (e.g., "self transfer", both "credited" and "debited" present, two account tails present) or explicit `type=="TRANSFER"`.
  - `findPairedTransferIds(...)`: pairs same-amount credit/debit within a 2h window with optional bank/tail/name matching; such pairs are excluded from spend/income totals.

- Manual add:
  - `addManual(amount, type, merchant, channel, whenTs=now)`: inserts a manual `Transaction` (amount stored in minor units, marked `source="MANUAL"`).

### SMS Pipeline
- Broadcast receiver: `SmsReceiver` (manifest-registered, priority 999) listens to `android.provider.Telephony.SMS_RECEIVED`.
- Importer: `SmsImporter`
  - Bulk import reads SMS inbox within N months, provides progress callbacks.
- Parsing/Regex overview:
  - `SmsParser.init(context)` initializes the regex-based SMS parser.
  - `SmsParser.parse(sender, body, ts)` orchestrates regex-based extraction and classification.
- Deduplication:
  - Before insert, `dao.exists(sender, body, ts)` is checked; DB also enforces a unique index on the same triple.

### Detailed Regex classification and parsing
- **Regex-only parsing** (no AI, no TensorFlow):
  - **Input**: SMS text processed through multiple regex patterns
  - **Output**: Transaction objects with:
    - Direction (DEBIT/CREDIT) based on keywords
    - Merchant names from prepositional phrases
    - Amounts from currency patterns
    - Transaction types (UPI/CARD/IMPS) from keywords
    - Account tails and bank identification

- Parser flow (`SmsParser.parse`):
  1) Calls `parseWithRegex(sender, body, ts)` for all SMS processing
  2) **Heuristic validation**: `looksLikeTransaction()` requires transaction verbs + at least 2 strong signals
  3) **Amount extraction**: Uses regex patterns for ₹, Rs., INR amounts
  4) **Type determination**: CREDIT/DEBIT based on keywords like "credited", "debited", "withdrawn"
  5) **Transfer detection**: `RecentAmountWindow.recordAndDetect()` identifies internal transfers within 3-minute windows
  6) **Entity extraction**: Channel, merchant, account tail, bank info via regex patterns
  7) **Final validation**: Ensures strong transaction signals before accepting

- **Regex patterns include**:
  - Amounts: `₹1,23,456.78`, `Rs.999/-`, `INR 2500`
  - Transaction verbs: `credited`, `debited`, `withdrawn`, `deposited`
  - Payment channels: `UPI`, `IMPS`, `NEFT`, `RTGS`, `POS`, `ATM`, `CARD`
  - Bank context: `balance`, `transaction`, `bank`, `branch`
  - Reference numbers: `Ref`, `UTR`, `Transaction ID`
  - Account numbers: `A/c XXXX1234`, `Account 5678`
  - Bank identification: Maps 10+ Indian bank sender prefixes

Notes:
- The app now uses **pure regex-based parsing** with no AI dependencies
- All TensorFlow Lite code has been removed
- Parsing is fast, reliable, and works offline
- No SMS content is sent to external servers

### UI Flow Details
- `HomeScreen`:
  - Top app bar shows app name and subtitle.
  - Range toggles for "This month" vs "Last 30 days" (maps to `HomeVm.RangeMode`).
  - "MonthlySummaryCard" shows balance, income, expenses for the active range (excluding likely transfers and pairs).
  - "QuickStatsCard" shows counts and averages for visible transactions.
  - "Recent Transactions" shows up to the latest 10. Tapping a card expands to reveal original SMS text.
  - FAB opens `AddManualTxnSheet` via `onAddTransaction` callback.

- `TransactionListScreen`:
  - Summary card computed from filtered list, excluding likely transfers and paired debit/credits.
  - Search by merchant/channel/body.
  - Filters: by transaction type (Income/Expense/Transfers), by payment channel (UPI/Card), and by date (Past 30 days, month chips, or explicit range).

- `AnalyticsScreen` and `SettingsScreen`:
  - Use the same `HomeVm` backing data; `SettingsScreen` hosts controls like re-import, clear, and stats (as implemented therein).

### Permissions and Privacy
- Requires `READ_SMS` and `RECEIVE_SMS` to import and listen for SMS.
- **Regex-only parsing**: No AI models, no external API calls, no SMS content sent to network.
- `INTERNET` permission removed (not needed for regex parsing).

### Error Handling/Resilience
- Defensive try/catch in `Application`, `MainActivity`, and `HomeVm` to prevent crashes from parsing/DB failures.
- DB creation includes fallback to destructive migration if previous migrations fail.
- Import process logs progress and continues despite individual parse/DB errors.

### How everything ties together
1. App launches → `SmartExpensesApp` initializes regex parser.
2. `MainActivity` composes `App()` → requests SMS permissions immediately.
3. After permissions granted, `HomeVm.importIfFirstRun()` runs.
4. `SmsImporter` + `SmsParser.parseWithRegex()` convert eligible SMS into `Transaction`s, deduped and saved by `TxnDao`.
5. `HomeVm` exposes `Flow` state for lists and totals → `HomeScreen`, `TransactionListScreen`, and others render reactive UI.
6. New incoming SMS trigger `SmsReceiver` (for live updates), and users can add manual transactions via the FAB.


