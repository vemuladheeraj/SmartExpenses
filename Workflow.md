## SmartExpenses - App Workflow

### Overview
SmartExpenses is a Jetpack Compose Android app that auto-extracts transactions from SMS using an on-device AI classifier and stores them in a Room database. The UI presents summaries and lists with filters. Manual transactions can also be added.

### App Startup
- Application class: `SmartExpensesApp`
  - Sets `SmartContextProvider.app` for global context access.
  - Eagerly initializes `SmsMultiTaskClassifier` by calling `loadModelWithFallback()`; this loads a bundled on-device TFLite model from assets (`assets/sms_multi_task.tflite`). If it cannot load, the system will continue with robust regex fallbacks.
  - Initializes `SmsParser`.

- Entry Activity: `MainActivity`
  - Creates `HomeVm` via `by viewModels()`.
  - UI content set with `setContent { App(vm, requestPerms) }`.

### First-run model and permissions flow
Inside `App(vm, requestPerms)`:
- No model download is performed. `ModelDownloadHelper.shouldShowDownloadDialog(context)` is hardcoded to `false`, so the dialog path is effectively disabled.
- As a result, the app requests `READ_SMS` and `RECEIVE_SMS` permissions immediately (via a `LaunchedEffect` once it sees no dialog is needed).
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
- Parsing/AI overview:
  - `SmsParser.init(context)` creates an `SmsMultiTaskClassifier` and attempts to load the bundled TFLite model. If unavailable, parsing falls back to regex heuristics.
  - `SmsParser.parse(sender, body, ts)` orchestrates classification and extraction, described in detail below.
- Deduplication:
  - Before insert, `dao.exists(sender, body, ts)` is checked; DB also enforces a unique index on the same triple.

### Detailed AI classification and parsing
- On-device AI (no downloads, no remote LLM):
  - Model file: `assets/sms_multi_task.tflite`.
  - Wrapper: `SmsMultiTaskClassifier`.
  - Inputs: tokenized SMS text as `int32[1, 200]`.
  - Outputs (5):
    - Direction logits `Float32[1,3]` → {DEBIT, CREDIT, NONE}.
    - Merchant NER `Float32[1,200,3]` (BIO tags).
    - Amount NER `Float32[1,200,3]` (BIO tags).
    - Transactional classification score `Float32[1,1]` (probability the SMS is transactional).
    - Type NER `Float32[1,200,3]` (BIO tags; e.g., UPI, CARD, IMPS).

- Classifier flow (`SmsMultiTaskClassifier.analyzeSms`):
  1) Ensure model is loaded; if not, attempt `loadModelWithFallback()` from assets; else use regex fallback.
  2) Preprocess: lowercase, strip punctuation, whitespace tokenize, hash tokens to a fixed-size vocabulary, pad/truncate to length 200, pack into a `ByteBuffer` as int32.
  3) Inference: run `runForMultipleInputsOutputs` with fixed output buffers mapped to the 5 heads.
  4) Postprocess:
     - `classification > 0.5` → transactional.
     - Direction = argmax of 3-class output.
     - Entities via BIO: walk 200 positions; choose tag with max prob per token; build entity spans for merchant/amount/type; pick first.
  5) If model not available or inference fails, use `analyzeSmsFallback` (regex-based amount/type/direction/merchant extraction with confidence heuristics).

- Parser flow (`SmsParser.parse`):
  1) Calls `classifier.analyzeSms(body)`.
  2) If AI says transactional and provides an amount and direction:
     - Convert amount string to minor units.
     - Map direction to `type` (DEBIT/CREDIT).
     - Compose `Transaction` using:
       - `channel` from AI type if present; else from regex `txnTypeRegex`.
       - `merchant` from AI merchant if present; cleaned.
       - `accountTail` via `accTailRegex`/`accTailLoose`.
       - `bank` inferred from `sender` prefix via `bankFromSender` map.
     - Short-window transfer detector: `RecentAmountWindow.recordAndDetect(amountMinor, type, ts)` marks patterns where identical amounts debit/credit within 3 minutes suggest internal transfers (used as a signal in higher-level summaries; the saved record remains typed CREDIT/DEBIT).
     - Return the built `Transaction`.
  3) Otherwise, fall back to `parseWithRegex`:
     - Heuristic precheck (`looksLikeTransaction`): requires transaction verbs and at least two strong signals (account tail/ref/date/bank context/bank known).
     - Extract amount; infer type from verbs; extract channel and merchant with prepositions; account tail; bank from sender.
     - Optionally consult AI again for confidence; treat as transactional if confidence ≥ 0.7, or ≥ 0.5 with solid regex signals, or strong regex-only signals.
     - Return `Transaction` if criteria met; else null.

Notes:
- The AI runs fully on-device in `SmsMultiTaskClassifier`; there is no remote LLM. `ModelDownloadHelper` is a no-op in this configuration, so no download dialog is ever shown.

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
- On-device model inference; no SMS content is sent to network by the parsing path. `INTERNET` is used for model download when needed and general connectivity checks.

### Error Handling/Resilience
- Defensive try/catch in `Application`, `MainActivity`, and `HomeVm` to prevent crashes from model/DB failures.
- DB creation includes fallback to destructive migration if previous migrations fail.
- Import process logs progress and continues despite individual parse/DB errors.

### How everything ties together
1. App launches → `SmartExpensesApp` initializes AI and parser.
2. `MainActivity` composes `App()` → shows model download dialog if needed.
3. After model is ready, permissions requested → on grant `HomeVm.importIfFirstRun()` runs.
4. `SmsImporter` + `SmsParser` convert eligible SMS into `Transaction`s, deduped and saved by `TxnDao`.
5. `HomeVm` exposes `Flow` state for lists and totals → `HomeScreen`, `TransactionListScreen`, and others render reactive UI.
6. New incoming SMS trigger `SmsReceiver` (for live updates), and users can add manual transactions via the FAB.


