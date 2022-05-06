package com.hover.stax.bounties

import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StrikethroughSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.hover.stax.R
import com.hover.stax.databinding.BountyListItemBinding

internal class BountyListItem(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
	private val binding: BountyListItemBinding

	private var bounty: Bounty? = null
	private var selectListener: SelectListener? = null
	fun setBounty(b: Bounty?, listener: SelectListener?) {
		bounty = b
		setContent()
		chooseState()
		selectListener = listener
	}

	private fun setContent() {
		binding.liTitle.setText(bounty!!.generateDescription(context))
		binding.liAmount.setText(
			context.getString(R.string.bounty_amount_with_currency, bounty!!.action.bounty_amount)
		)
	}

	private fun chooseState() {
		if (bounty!!.hasASuccessfulTransaction()) {
			setState(R.color.muted_green, R.string.done, R.drawable.ic_check, false, null)
		} else if (bounty!!.isLastTransactionFailed() && !bounty!!.action.bounty_is_open) {
			setState(R.color.stax_bounty_red_bg, R.string.bounty_transaction_failed, R.drawable.ic_info_red,
				false,
				navTransactionDetail()
			)
		} else if (bounty!!.isLastTransactionFailed() && bounty!!.action.bounty_is_open) {
			setState(R.color.stax_bounty_red_bg, R.string.bounty_transaction_failed_try_again, R.drawable.ic_info_red,
				true,
				showBountyDetail()
			)
		} else if (!bounty!!.action.bounty_is_open) { // This bounty is closed and done by another user
			setState(R.color.lighter_grey, 0, 0, false, null)
		} else if (bounty!!.transactionCount > 0) { // Bounty is open and with a transaction by current user
			setState(R.color.pending_brown, R.string.bounty_pending_short_desc, R.drawable.ic_warning,
				true,
				navTransactionDetail()
			)
		} else setState(R.color.cardViewColor, 0, 0, true, showBountyDetail())
	}

	private fun navTransactionDetail(): OnClickListener {
		return OnClickListener {
			selectListener!!.viewTransactionDetail(
				bounty!!.transactions[bounty!!.lastTransactionIndex()].uuid
			)
		}
	}

	private fun showBountyDetail(): OnClickListener {
		return OnClickListener { bounty?.let { selectListener!!.viewBountyDetail(bounty!!) } }
	}

	private fun setState(
		color: Int,
		noticeString: Int,
		noticeIcon: Int,
		isOpen: Boolean,
		listener: OnClickListener?
	) {
		setBackgroundColor(getContext().getResources().getColor(color))
		if (noticeString != 0) {
			binding.liDetail.setText(
				HtmlCompat.fromHtml(getContext().getString(noticeString), HtmlCompat.FROM_HTML_MODE_LEGACY)
			)
			binding.liDetail.setMovementMethod(LinkMovementMethod.getInstance())
		}
		binding.liDetail.setCompoundDrawablesWithIntrinsicBounds(noticeIcon, 0, 0, 0)
		binding.liDetail.setVisibility(if (noticeString != 0) View.VISIBLE else View.GONE)
		if (!isOpen) strikeThrough(binding.liAmount)
		if (!isOpen) strikeThrough(binding.liTitle)
		setOnClickListener(listener)
	}

	private fun strikeThrough(textView: TextView) {
		textView.setText(textView.getText(), TextView.BufferType.SPANNABLE)
		val spannable: Spannable = textView.getText() as Spannable
		spannable.setSpan(
			StrikethroughSpan(),
			0,
			textView.getText().length,
			Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		)
	}

	interface SelectListener {
		fun viewTransactionDetail(uuid: String?)
		fun viewBountyDetail(b: Bounty)
	}

	init {
		binding = BountyListItemBinding.inflate(LayoutInflater.from(context), this, true)
	}
}