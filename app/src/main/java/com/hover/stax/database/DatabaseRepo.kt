package com.hover.stax.database

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hover.sdk.actions.HoverAction
import com.hover.sdk.actions.HoverActionDao
import com.hover.sdk.database.HoverRoomDatabase
import com.hover.sdk.sims.SimInfo
import com.hover.sdk.sims.SimInfoDao
import com.hover.sdk.transactions.Transaction
import com.hover.sdk.transactions.TransactionContract
import com.hover.stax.R
import com.hover.stax.accounts.Account
import com.hover.stax.accounts.AccountDao
import com.hover.stax.accounts.ChannelWithAccounts
import com.hover.stax.channels.Channel
import com.hover.stax.channels.ChannelDao
import com.hover.stax.contacts.ContactDao
import com.hover.stax.contacts.StaxContact
import com.hover.stax.requests.Request
import com.hover.stax.requests.RequestDao
import com.hover.stax.requests.Shortlink
import com.hover.stax.schedules.Schedule
import com.hover.stax.schedules.ScheduleDao
import com.hover.stax.transactions.StaxTransaction
import com.hover.stax.transactions.TransactionDao
import com.hover.stax.utils.AnalyticsUtil
import com.hover.stax.utils.DateUtils.lastMonth
import com.hover.stax.utils.paymentLinkCryptography.Encryption
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.security.NoSuchAlgorithmException

//TODO split this up into smaller constituent repos
class DatabaseRepo(db: AppDatabase, sdkDb: HoverRoomDatabase) {

    private val decryptedRequest: MutableLiveData<Request> = MutableLiveData()

    private val channelDao: ChannelDao = db.channelDao()
    private val actionDao: HoverActionDao = sdkDb.actionDao()
    private val requestDao: RequestDao = db.requestDao()
    private val scheduleDao: ScheduleDao = db.scheduleDao()
    private val simDao: SimInfoDao = sdkDb.simDao()
    private val transactionDao: TransactionDao = db.transactionDao()
    private val contactDao: ContactDao = db.contactDao()
    private val accountDao: AccountDao = db.accountDao()

    // Channels
    val publishedChannels: LiveData<List<Channel>> = channelDao.publishedChannels
    val allChannels = channelDao.allChannels

    val selected: LiveData<List<Channel>> = channelDao.getSelected(true)

    fun getChannelsAndAccounts(): List<ChannelWithAccounts> = channelDao.getChannelsAndAccounts()

    fun getChannelAndAccounts(id: Int): ChannelWithAccounts? = channelDao.getChannelAndAccounts(id)

    fun getChannel(id: Int): Channel? {
        return channelDao.getChannel(id)
    }

    fun getLiveChannel(id: Int): LiveData<Channel> {
        return channelDao.getLiveChannel(id)
    }

    fun getChannels(ids: IntArray): LiveData<List<Channel>> {
        return channelDao.getChannels(ids)
    }

    fun getChannelsByIds(ids: List<Int>): List<Channel> = channelDao.getChannelsByIds(ids)

    fun getChannelsByCountry(channelIds: IntArray, countryCode: String): LiveData<List<Channel>> {
        return channelDao.getChannels(countryCode.uppercase(), channelIds)
    }

    fun getChannelsByCountry(countryCode: String): List<Channel> {
        return channelDao.getChannels(countryCode.uppercase())
    }

    fun update(channel: Channel?) = channelDao.update(channel)

    fun insert(channel: Channel) = channelDao.insert(channel)

    // SIMs
    val presentSims: List<SimInfo>
        get() = simDao.present

    fun getSims(hnis: Array<String?>?): List<SimInfo> {
        return simDao.getPresentByHnis(hnis)
    }

    // Actions
    fun getAction(public_id: String): HoverAction? {
        return actionDao.getAction(public_id)
    }

    fun getLiveAction(public_id: String?): LiveData<HoverAction> {
        return actionDao.getLiveAction(public_id)
    }

    fun getChannelActions(channelId: Int): LiveData<List<HoverAction>> {
        return actionDao.getLiveChannelActions(channelId)
    }

    fun getLiveActions(channelIds: IntArray, types: List<String>): LiveData<List<HoverAction>> {
        return actionDao.getLiveActions(channelIds, types)
    }

    fun getTransferActions(channelId: Int): List<HoverAction> {
        return actionDao.getTransferActions(channelId)
    }

    fun getActions(channelId: Int, type: String?): List<HoverAction> {
        return actionDao.getActions(channelId, type)
    }

    fun getActions(channelIds: IntArray?, type: String?): List<HoverAction> {
        return actionDao.getActions(channelIds, type)
    }

    fun getActions(channelIds: IntArray?, recipientInstitutionId: Int): List<HoverAction> {
        return actionDao.getActions(channelIds, recipientInstitutionId, HoverAction.P2P)
    }

    val bountyActions: LiveData<List<HoverAction>>
        get() = actionDao.bountyActions

    // Transactions
    val completeAndPendingTransferTransactions: LiveData<List<StaxTransaction>>?
        get() = transactionDao.getCompleteAndPendingTransfers()

    val bountyTransactions: LiveData<List<StaxTransaction>>?
        get() = transactionDao.bountyTransactions

    @SuppressLint("DefaultLocale")
    suspend fun hasTransactionLastMonth(): Boolean {
        return transactionDao.getTransactionCount(String.format("%02d", lastMonth().first), lastMonth().second.toString())!! > 0
    }

    fun getAccountTransactions(account: Account): LiveData<List<StaxTransaction>>? {
        return transactionDao.getAccountTransactions(account.id)
    }

    @SuppressLint("DefaultLocale")
    fun getSpentAmount(accountId: Int, month: Int, year: Int): LiveData<Double>? {
        return transactionDao.getTotalAmount(accountId, String.format("%02d", month), year.toString())
    }

    @SuppressLint("DefaultLocale")
    fun getFees(accountId: Int, year: Int): LiveData<Double>? {
        return transactionDao.getTotalFees(accountId, year.toString())
    }

    fun getTransaction(uuid: String?): StaxTransaction? {
        return transactionDao.getTransaction(uuid)
    }

    fun getTransactionAsync(uuid: String): Flow<StaxTransaction> = transactionDao.getTransactionAsync(uuid)

    fun insertOrUpdateTransaction(intent: Intent, action: HoverAction, contact: StaxContact, c: Context) {
        AppDatabase.databaseWriteExecutor.execute {
            try {
                var t = getTransaction(intent.getStringExtra(TransactionContract.COLUMN_UUID))

                if (t == null) {
                    AnalyticsUtil.logAnalyticsEvent(c.getString(R.string.transaction_started), c, true)
                    t = StaxTransaction(intent, action, contact, c)
                    transactionDao.insert(t)
                    t = transactionDao.getTransaction(t.uuid)
                } else {
                    AnalyticsUtil.logAnalyticsEvent(c.getString(R.string.transaction_completed), c, true)
                    t.update(intent, action, contact, c)
                    transactionDao.update(t)
                }
                Timber.e("save t with uuid: %s", t?.uuid)
            } catch (e: Exception) {
                Timber.e(e, "error")
            }
        }
    }

    // Contacts
    val allContacts: LiveData<List<StaxContact>>
        get() = contactDao.all

    fun getContacts(ids: Array<String>): List<StaxContact> {
        return contactDao[ids]
    }

    fun getLiveContacts(ids: Array<String>): LiveData<List<StaxContact>> {
        return contactDao.getLive(ids)
    }

    fun getContact(id: String?): StaxContact? {
        return contactDao[id]
    }

    suspend fun getContactAsync(id: String?): StaxContact? {
        return contactDao.getAsync(id)
    }

    fun getContactByPhone(phone: String): StaxContact? {
        return contactDao.getByPhone("%$phone%")
    }

    fun getLiveContact(id: String?): LiveData<StaxContact> {
        return contactDao.getLive(id)
    }

    fun save(contact: StaxContact) {
        AppDatabase.databaseWriteExecutor.execute {
            if (getContact(contact.id) == null && contact.accountNumber != null) {
                try {
                    contactDao.insert(contact)
                } catch (e: Exception) {
                    AnalyticsUtil.logErrorAndReportToFirebase(TAG, "failed to insert contact", e)
                }
            } else contactDao.update(contact)
        }
    }

    // Schedules
    val futureTransactions: LiveData<List<Schedule>>
        get() = scheduleDao.liveFuture

    fun getFutureTransactions(channelId: Int): LiveData<List<Schedule>> {
        return scheduleDao.getLiveFutureByChannelId(channelId)
    }

    val transactionsForAppReview: LiveData<List<StaxTransaction>>?
        get() = transactionDao.transactionsForAppReview

    fun getSchedule(id: Int): Schedule? {
        return scheduleDao.get(id)
    }

    fun insert(schedule: Schedule?) {
        AppDatabase.databaseWriteExecutor.execute { scheduleDao.insert(schedule) }
    }

    fun update(schedule: Schedule?) {
        AppDatabase.databaseWriteExecutor.execute { scheduleDao.update(schedule) }
    }

    fun delete(schedule: Schedule?) {
        AppDatabase.databaseWriteExecutor.execute { scheduleDao.delete(schedule) }
    }

    // Requests
    val liveRequests: LiveData<List<Request>>
        get() = requestDao.liveUnmatched

    fun getLiveRequests(channelId: Int): LiveData<List<Request>> {
        return requestDao.getLiveUnmatchedByChannel(channelId)
    }

    val requests: List<Request>
        get() = requestDao.unmatched

    fun getRequest(id: Int): Request? {
        return requestDao[id]
    }

    fun decrypt(encrypted: String, c: Context): LiveData<Request> {
        decryptedRequest.value = null
        val removedBaseUrlString = encrypted.replace(c.getString(R.string.payment_root_url, ""), "")

        //Only old stax versions contains ( in the link
        if (removedBaseUrlString.contains("(")) decryptRequestForOldVersions(removedBaseUrlString)
        else decryptRequest(removedBaseUrlString, c)
        return decryptedRequest
    }

    private fun decryptRequest(param: String, c: Context) {
        decryptedRequest.postValue(Request(Request.decryptBijective(param, c)))
    }

    private fun decryptRequestForOldVersions(param: String) {
        var params = param
        try {
            val e = Request.encryptionSettings.build()
            if (Request.isShortLink(params)) {
                params = Shortlink(params).expand()
            }
            e.decryptAsync(params.replace("[(]".toRegex(), "+"), object : Encryption.Callback {
                override fun onSuccess(result: String) {
                    decryptedRequest.postValue(Request(result))
                }

                override fun onError(exception: Exception) {
                    AnalyticsUtil.logErrorAndReportToFirebase(TAG, "failed link decryption", exception)
                }
            })
        } catch (e: NoSuchAlgorithmException) {
            AnalyticsUtil.logErrorAndReportToFirebase(TAG, "decryption failure", e)
        }
    }

    fun insert(request: Request?) {
        AppDatabase.databaseWriteExecutor.execute { requestDao.insert(request) }
    }

    fun update(request: Request?) {
        AppDatabase.databaseWriteExecutor.execute { requestDao.update(request) }
    }

    fun delete(request: Request?) {
        AppDatabase.databaseWriteExecutor.execute { requestDao.delete(request) }
    }

//    val allAccountsLive: LiveData<List<Account>> = accountDao.getAllAccountsLive()

    fun getAllAccounts(): List<Account> = accountDao.getAllAccounts()

    fun getAccountsCount(): Int = accountDao.getDataCount()

    fun getAccounts(channelId: Int): List<Account> = accountDao.getAccounts(channelId)

    fun getDefaultAccount(): Account? = accountDao.getDefaultAccount()

    fun getAccount(id: Int): Account? = accountDao.getAccount(id)

    fun getLiveAccount(id: Int): LiveData<Account> = accountDao.getLiveAccount(id)

    fun getAccounts(): Flow<List<Account>> = accountDao.getAccounts()

    private fun getAccount(name: String, channelId: Int): Account? = accountDao.getAccount(name, channelId)

    fun saveAccounts(accounts: List<Account>) {
        accounts.forEach { account ->
            val acct = getAccount(account.name, account.channelId)

            try {
                AppDatabase.databaseWriteExecutor.execute {
                    if (acct == null) {
                        accountDao.insert(account)
                    } else {
                        accountDao.update(account)
                    }
                }
            } catch (e: Exception) {
                AnalyticsUtil.logErrorAndReportToFirebase(TAG, "failed to insert/update account", e)
            }
        }
    }

    fun insert(account: Account) = AppDatabase.databaseWriteExecutor.execute { accountDao.insert(account) }

    fun update(account: Account?) = account?.let { AppDatabase.databaseWriteExecutor.execute { accountDao.update(it) } }

    fun delete(account: Account) = AppDatabase.databaseWriteExecutor.execute { accountDao.delete(account) }

    fun deleteAccount(channelId: Int, name: String) { accountDao.delete(channelId, name) }

    companion object {
        private val TAG = DatabaseRepo::class.java.simpleName
    }
}