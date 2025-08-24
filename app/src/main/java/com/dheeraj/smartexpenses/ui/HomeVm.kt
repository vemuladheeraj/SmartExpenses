package com.dheeraj.smartexpenses.ui



import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.SmsImporter
import com.dheeraj.smartexpenses.sms.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).txnDao()

    private val now = MutableStateFlow(System.currentTimeMillis())
    fun refresh() { now.value = System.currentTimeMillis() }

    private fun monthBounds(epoch: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = epoch
        
        // Set to first day of month at 00:00:00
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        
        // Set to first day of next month at 00:00:00, then subtract 1ms
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.timeInMillis - 1
        
        return start to end
    }

    private val range = now.map { monthBounds(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L to Long.MAX_VALUE)

    val items = range.flatMapLatest { (s,e) -> dao.inRange(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalDebit = range.flatMapLatest { (s,e) -> dao.totalDebits(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit = range.flatMapLatest { (s,e) -> dao.totalCredits(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Manual add */
    fun addManual(amount: Double, type: String, merchant: String?, channel: String?, whenTs: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = Transaction(
                ts = whenTs,
                amount = amount,
                type = type.uppercase(Locale.getDefault()),
                channel = channel,
                merchant = merchant,
                accountTail = null,
                bank = null,
                source = "MANUAL",
                rawSender = "MANUAL",
                rawBody = ""
            )
            dao.insert(t)
        }
    }

    /** First-run import of existing SMS */
    fun importRecentSms(monthsBack: Long = 6) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            SmsImporter.importRecent(ctx, monthsBack) { sender, body, ts ->
                SmsParser.parse(sender, body, ts)?.let { transaction ->
                    launch {
                        dao.insert(transaction)
                    }
                }
            }
        }
    }
}
