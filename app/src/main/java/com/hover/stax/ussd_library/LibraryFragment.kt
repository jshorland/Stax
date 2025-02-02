package com.hover.stax.ussd_library

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.hover.stax.R
import com.hover.stax.channels.Channel
import com.hover.stax.countries.CountryAdapter
import com.hover.stax.databinding.FragmentLibraryBinding

import com.hover.stax.utils.AnalyticsUtil
import com.hover.stax.utils.UIHelper
import com.hover.stax.views.RequestServiceDialog
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class LibraryFragment : Fragment(), CountryAdapter.SelectListener {

    private val viewModel: LibraryViewModel by viewModel()
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsUtil.logAnalyticsEvent(getString(R.string.visit_screen, LibraryFragment::class.java.simpleName), requireActivity())

        binding.countryCard.showProgressIndicator()
        binding.countryDropdown.setListener(this)
        binding.shortcodes.layoutManager = UIHelper.setMainLinearManagers(requireActivity())

        setupEmptyState()
        setSearchInputWatcher()
        setObservers()
    }

    private fun setObservers() {
        with(viewModel) {
            stagedChannels.observe(viewLifecycleOwner) { Timber.i("staged channels loaded")}
            filteredChannels.observe(viewLifecycleOwner) { it?.let { updateList(it) } }
            country.observe(viewLifecycleOwner) { it?.let { binding.countryDropdown.setDropdownValue(it) } }
            allChannels.observe(viewLifecycleOwner) { it?.let { binding.countryDropdown.updateChoices(it, viewModel.country.value) } }
        }
    }

    private fun setupEmptyState() {
        binding.emptyState.root.visibility = View.GONE
        binding.emptyState.informUs.setOnClickListener {
            RequestServiceDialog(requireActivity()).showIt()
        }
    }

    private fun setSearchInputWatcher() {
        val searchInputWatcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                viewModel.runChannelFilter(charSequence.toString())
            }
        }
        binding.searchInput.addTextChangedListener(searchInputWatcher)
    }

    private fun updateList(channels: List<Channel>) {
        binding.countryCard.hideProgressIndicator()

        if (!channels.isNullOrEmpty()) showList(channels)
        else if (viewModel.isInSearchMode()) showEmptyState()
        else showLoading()
    }

    private fun showList(channels: List<Channel>) {
        binding.shortcodes.adapter = LibraryChannelsAdapter(channels)
        binding.emptyState.root.visibility = View.GONE
        binding.shortcodesParent.visibility = VISIBLE
    }

    private fun showEmptyState() {
        val content = resources.getString(R.string.no_accounts_found_desc,  viewModel.filterQuery.value!!)
        binding.emptyState.noAccountFoundDesc.apply {
            text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
        }
        binding.emptyState.root.visibility = VISIBLE
        binding.shortcodesParent.visibility = View.GONE
    }

    private fun showLoading() {
        binding.countryCard.showProgressIndicator()
        binding.emptyState.root.visibility = View.GONE
    }

    override fun countrySelect(countryCode: String) {
        viewModel.setCountry(countryCode)
    }
}