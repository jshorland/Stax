package com.hover.stax.hover

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hover.sdk.transactions.TransactionContract
import com.hover.stax.database.DatabaseRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class TransactionReceiver : BroadcastReceiver(), KoinComponent {

    private val repo: DatabaseRepo by inject()

    override fun onReceive(context: Context, intent: Intent) {
        updateBalance(repo, intent)
        updateTransaction(repo, intent, context)
    }

    private fun updateBalance(repo: DatabaseRepo, intent: Intent) {
        intent.extras?.keySet()?.forEach { Timber.e(it) }

        if (intent.hasExtra(TransactionContract.COLUMN_PARSED_VARIABLES)) {
            val parsedVariables = intent.getSerializableExtra(TransactionContract.COLUMN_PARSED_VARIABLES) as? HashMap<String, String>

            parsedVariables?.keys?.forEach {
                Timber.e("$it - ${parsedVariables[it]}")
            }

            if (parsedVariables != null && parsedVariables.containsKey("balance")) {
                GlobalScope.launch(Dispatchers.IO) {
                    val action = repo.getAction(intent.getStringExtra(TransactionContract.COLUMN_ACTION_ID))
                    val channel = repo.getChannel(action.channel_id)
                    channel.updateBalance(parsedVariables)
                    repo.update(channel)
                }
            }
        }
    }

    private fun updateTransaction(repo: DatabaseRepo, intent: Intent, c: Context) {
        repo.insertOrUpdateTransaction(intent, c)
    }
}