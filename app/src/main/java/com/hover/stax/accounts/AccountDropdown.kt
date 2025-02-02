package com.hover.stax.accounts

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hover.sdk.actions.HoverAction
import com.hover.sdk.sims.SimInfo
import com.hover.stax.R
import com.hover.stax.channels.Channel
import com.hover.stax.channels.ChannelsViewModel
import com.hover.stax.utils.Constants
import com.hover.stax.utils.UIHelper
import com.hover.stax.views.StaxDropdownLayout
import timber.log.Timber


class AccountDropdown(context: Context, attributeSet: AttributeSet) : StaxDropdownLayout(context, attributeSet) {

    private var showSelected: Boolean = true
    private var helperText: String? = null
    private var highlightListener: HighlightListener? = null
    private var accountFetchListener: AccountFetchListener? = null

    private var accountList: List<Account> = ArrayList()

    var highlightedAccount: Account? = null

    init {
        getAttrs(context, attributeSet)
    }

    private fun getAttrs(context: Context, attributeSet: AttributeSet) {
        val attributes = context.theme.obtainStyledAttributes(attributeSet, R.styleable.ChannelDropdown, 0, 0)

        try {
            showSelected = attributes.getBoolean(R.styleable.ChannelDropdown_show_selected, true)
            helperText = attributes.getString(R.styleable.ChannelDropdown_initial_helper_text)
        } finally {
            attributes.recycle()
        }
    }

    fun setListener(listener: HighlightListener) {
        highlightListener = listener
    }

    private fun accountUpdate(accounts: List<Account>) {
        if (!accounts.isNullOrEmpty()) {
            updateChoices(accounts)
        } else if (!hasExistingContent()) {
            setState(context.getString(R.string.accounts_error_no_accounts), NONE)
        }
    }

    private fun setDropdownValue(account: Account?) {
        account?.let {
            autoCompleteTextView.setText(it.alias, false)

            val target = object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    autoCompleteTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(resource, null, null, null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    autoCompleteTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_grey_circle_small, 0, 0, 0)
                }
            }

            UIHelper.loadImage(context, account.logoUrl, target)

            if (account.name == Constants.PLACEHOLDER) {
                accountFetchListener?.fetchAccounts(account)
            } else {
                highlightedAccount = account
            }
        }
    }

    private fun updateChoices(accounts: List<Account>) {
        if (highlightedAccount == null) setDropdownValue(null)
        accountList = accounts
        val adapter = AccountDropdownAdapter(accounts, context)
        autoCompleteTextView.apply {
            setAdapter(adapter)
            setOnItemClickListener { parent, _, position, _ -> onSelect(parent.getItemAtPosition(position) as Account) }
        }
    }

    fun setCurrentAccount() = onSelect(accountList.firstOrNull { it.isDefault })

    private fun onSelect(account: Account?) {
        setDropdownValue(account)
        account?.let { highlightListener?.highlightAccount(it) }
    }

    private fun hasExistingContent(): Boolean = autoCompleteTextView.adapter != null && autoCompleteTextView.adapter.count > 0

    fun setObservers(viewModel: ChannelsViewModel, lifecycleOwner: LifecycleOwner) {
        with(viewModel) {
            val simsObserver = object: Observer<List<SimInfo>> {
                override fun onChanged(t: List<SimInfo>?) {
                    Timber.i("Got sims ${t?.size}")
                }
            }
            val hniListObserver = object : Observer<List<String>> {
                override fun onChanged(t: List<String>?) {
                    Timber.i("Got new sim hni list $t")
                }
            }
            val selectedObserver = object : Observer<List<Channel>> {
                override fun onChanged(t: List<Channel>?) {
                    Timber.e("Got new selected channels ${t?.size}")
                }
            }
            val accountObserver = object: Observer<Account> {
                override fun onChanged(t: Account?) {
                    setDropdownValue(t)
                }
            }

            sims.observe(lifecycleOwner, simsObserver)
            simHniList.observe(lifecycleOwner, hniListObserver)
            accounts.observe(lifecycleOwner) { accountUpdate(it) }
            activeAccount.observe(lifecycleOwner, accountObserver)

            selectedChannels.observe(lifecycleOwner, selectedObserver)
            activeChannel.observe(lifecycleOwner) { if (it != null && showSelected) setState(helperText, NONE); Timber.e("Setting state null") }
            channelActions.observe(lifecycleOwner) { setState(it, viewModel) }
        }
    }

    private fun setState(actions: List<HoverAction>, viewModel: ChannelsViewModel) {
        when {
            viewModel.activeChannel.value != null && (actions.isNullOrEmpty()) -> setState(
                context.getString(
                    R.string.no_actions_fielderror,
                    HoverAction.getHumanFriendlyType(context, viewModel.getActionType())
                ), ERROR
            )

            !actions.isNullOrEmpty() && actions.size == 1 && !actions.first().requiresRecipient() && viewModel.getActionType() != HoverAction.BALANCE ->
                setState(
                    context.getString(
                        if (actions.first().transaction_type == HoverAction.AIRTIME) R.string.self_only_airtime_warning
                        else R.string.self_only_money_warning
                    ), INFO
                )

            viewModel.activeChannel.value != null && showSelected -> setState(helperText, SUCCESS)
        }
    }

    fun setFetchAccountListener(listener: AccountFetchListener) {
        accountFetchListener = listener
    }

    interface HighlightListener {
        fun highlightAccount(account: Account)
    }

    interface AccountFetchListener {
        fun fetchAccounts(account: Account)
    }
}