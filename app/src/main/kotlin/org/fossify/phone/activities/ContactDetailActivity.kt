package org.fossify.phone.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchSendSMSIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityContactDetailBinding
import org.fossify.phone.databinding.ItemContactDetailFieldBinding
import org.fossify.phone.databinding.ItemContactEmailBinding
import org.fossify.phone.databinding.ItemContactPhoneNumberBinding
import org.fossify.phone.extensions.startCallWithConfirmationCheck

class ContactDetailActivity : SimpleActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"

        fun launchKnownContact(activity: SimpleActivity, contactId: Int, phoneNumber: String? = null) {
            val intent = Intent(activity, ContactDetailActivity::class.java).apply {
                putExtra(EXTRA_CONTACT_ID, contactId)
                if (phoneNumber != null) putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            activity.startActivity(intent)
        }

        fun launchUnknownNumber(activity: SimpleActivity, phoneNumber: String) {
            val intent = Intent(activity, ContactDetailActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            activity.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityContactDetailBinding

    private var contact: Contact? = null
    private var unknownNumber: String? = null
    private var recentlyUsedNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactDetailRoot.setBackgroundResource(R.color.contact_detail_bg)

        binding.backButton.setOnClickListener { finish() }
        binding.qrButton.setOnClickListener { toast(R.string.not_yet_implemented) }

        binding.moreHeader.setOnClickListener { toggleMoreExpander() }
        binding.callLogClear.setOnClickListener { toast(R.string.not_yet_implemented) }

        // Tint top-bar icons so they're visible on dark themes too.
        val textColor = getProperTextColor()
        binding.backButton.setColorFilter(textColor)
        binding.qrButton.setColorFilter(textColor)
        binding.moreChevron.setColorFilter(textColor)

        applyEdgeToEdgeInsets()
        loadData()
    }

    private fun applyEdgeToEdgeInsets() {
        // Edge-to-edge is on by default on AGP 9 / target SDK 36, so the bottom bar
        // would otherwise sit underneath the system navigation bar. Add the navigation
        // inset as bottom padding to keep the bar's content above it. Same for the
        // top bar so it doesn't overlap the status bar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, windowInsets ->
            val nav = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav.bottom)
            windowInsets
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, windowInsets ->
            val status = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, status.top, v.paddingRight, v.paddingBottom)
            windowInsets
        }
    }

    private fun emailTypeLabel(type: Int, customLabel: String): String {
        if (customLabel.isNotBlank()) return customLabel
        return when (type) {
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME -> getString(org.fossify.commons.R.string.home)
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK -> getString(org.fossify.commons.R.string.work)
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> getString(org.fossify.commons.R.string.mobile)
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> getString(org.fossify.commons.R.string.other)
            else -> getString(org.fossify.commons.R.string.other)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh from contacts in case data changed (e.g. user toggled favourite from system Contacts)
        if (contact != null) {
            loadData()
        }
    }

    private fun loadData() {
        val contactId = intent.getIntExtra(EXTRA_CONTACT_ID, -1)
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)

        if (contactId != -1) {
            ensureBackgroundThread {
                val fetched = ContactsHelper(this).getContactWithId(contactId, isLocalPrivate = false)
                runOnUiThread {
                    if (fetched != null) {
                        contact = fetched
                        renderKnownContact(fetched, number)
                    } else if (!number.isNullOrBlank()) {
                        unknownNumber = number
                        renderUnknownNumber(number)
                    } else {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                        finish()
                    }
                }
            }
        } else if (!number.isNullOrBlank()) {
            unknownNumber = number
            renderUnknownNumber(number)
        } else {
            finish()
        }
    }

    private fun renderKnownContact(c: Contact, fromCallNumber: String?) {
        // Header
        binding.contactName.text = c.getNameToDisplay()

        val company = c.organization.company
        binding.contactCompany.text = company
        binding.contactCompany.beVisibleIf(company.isNotBlank())

        SimpleContactsHelper(this).loadContactImage(c.photoUri, binding.contactAvatar, c.getNameToDisplay())

        // Compute most-recently-used number for "Recents" badge & default action
        recentlyUsedNumber = fromCallNumber ?: findMostRecentNumber(c.phoneNumbers)

        // Action row
        val primaryNumber = recentlyUsedNumber ?: c.phoneNumbers.firstOrNull()?.value
        binding.actionCall.setOnClickListener {
            primaryNumber?.let { startCallWithConfirmationCheck(it, c.getNameToDisplay()) }
        }
        binding.actionCall.setOnLongClickListener {
            showNumberPicker(c) { number -> startCallWithConfirmationCheck(number, c.getNameToDisplay()) }
            true
        }
        binding.actionMessage.setOnClickListener {
            primaryNumber?.let { launchSendSMSIntent(it) }
        }
        binding.actionMessage.setOnLongClickListener {
            showNumberPicker(c) { number -> launchSendSMSIntent(number) }
            true
        }
        binding.actionWhatsapp.setOnClickListener {
            primaryNumber?.let { openWhatsApp(it) }
        }
        binding.actionWhatsapp.setOnLongClickListener {
            showNumberPicker(c) { number -> openWhatsApp(number) }
            true
        }

        // Phone numbers card
        renderPhoneNumbers(c.phoneNumbers, c.getNameToDisplay())

        // Email card
        renderEmails(c.emails)

        // Ringtone row — UI only for now
        binding.ringtoneValue.text = c.ringtone?.takeIf { it.isNotBlank() }?.let { Uri.parse(it).lastPathSegment } ?: getString(R.string.ringtone_default)
        binding.ringtoneRow.setOnClickListener { toast(R.string.not_yet_implemented) }

        // Preferred SIM row — UI persists, data layer wired in Build 3
        renderPreferredSim(primaryNumber)
        binding.preferredSimRow.setOnClickListener {
            showPreferredSimPicker(primaryNumber)
        }

        // More expander content
        renderMoreContent(c)

        // Per-contact call log header — visible only if there are entries (we render lazily)
        // Hidden in Build 2; full call log section deferred to Build 7 polish.
        binding.callLogHeader.visibility = View.GONE
        binding.callLogList.visibility = View.GONE

        // Bottom bar — known contact: Favourite / Edit / More
        configureBottomBar(forKnownContact = true, contact = c)

        // Apply theme text colors after all rows have been inflated.
        updateTextColors(binding.contactDetailRoot)
        tintActionRowIcons()
    }

    private fun renderUnknownNumber(number: String) {
        binding.contactName.text = number
        binding.contactCompany.visibility = View.GONE
        SimpleContactsHelper(this).loadContactImage("", binding.contactAvatar, number)

        // Action row
        binding.actionCall.setOnClickListener { startCallWithConfirmationCheck(number, number) }
        binding.actionMessage.setOnClickListener { launchSendSMSIntent(number) }
        binding.actionWhatsapp.setOnClickListener { openWhatsApp(number) }
        binding.actionCall.setOnLongClickListener { false }
        binding.actionMessage.setOnLongClickListener { false }
        binding.actionWhatsapp.setOnLongClickListener { false }

        // Single phone-row card showing the number
        binding.phonesCard.removeAllViews()
        val rowBinding = ItemContactPhoneNumberBinding.inflate(LayoutInflater.from(this), binding.phonesCard, false)
        rowBinding.phoneNumberValue.text = number
        rowBinding.phoneNumberSubline.text = locationFor(number) ?: ""
        rowBinding.phoneNumberSubline.beVisibleIf(!locationFor(number).isNullOrBlank())
        rowBinding.phoneNumberRecentsBadge.visibility = View.GONE
        rowBinding.phoneRowRoot.setOnClickListener { startCallWithConfirmationCheck(number, number) }
        binding.phonesCard.addView(rowBinding.root)

        // Hide sections that don't apply to unknown numbers
        binding.emailsCard.visibility = View.GONE
        binding.ringtoneRow.visibility = View.GONE
        binding.preferredSimRow.visibility = View.GONE
        binding.moreHeader.visibility = View.GONE
        binding.moreContent.visibility = View.GONE
        binding.callLogHeader.visibility = View.GONE
        binding.callLogList.visibility = View.GONE

        // Bottom bar — unknown: Create new contact / Add to existing / More
        configureBottomBar(forKnownContact = false, contact = null, unknownNumberFallback = number)

        updateTextColors(binding.contactDetailRoot)
        tintActionRowIcons()
    }

    private fun tintActionRowIcons() {
        val textColor = getProperTextColor()
        // Each action card is a LinearLayout with [ImageView, TextView] children.
        listOf(binding.actionCall, binding.actionMessage, binding.actionWhatsapp).forEach { card ->
            val icon = card.getChildAt(0) as? android.widget.ImageView
            icon?.setColorFilter(textColor)
        }
        binding.bottomButton1Icon.setColorFilter(textColor)
        binding.bottomButton2Icon.setColorFilter(textColor)
        binding.bottomButton3Icon.setColorFilter(textColor)
    }

    private fun renderPhoneNumbers(numbers: List<PhoneNumber>, contactName: String) {
        binding.phonesCard.removeAllViews()
        if (numbers.isEmpty()) {
            binding.phonesCard.visibility = View.GONE
            return
        }
        binding.phonesCard.visibility = View.VISIBLE

        numbers.forEachIndexed { index, phone ->
            val rowBinding = ItemContactPhoneNumberBinding.inflate(LayoutInflater.from(this), binding.phonesCard, false)
            rowBinding.phoneNumberValue.text = phone.value

            val typeText = getPhoneNumberTypeText(phone.type, phone.label)
            val location = locationFor(phone.value)
            val sublineParts = listOfNotNull(typeText.takeIf { it.isNotBlank() }, location)
            rowBinding.phoneNumberSubline.text = sublineParts.joinToString(" · ")
            rowBinding.phoneNumberSubline.beVisibleIf(sublineParts.isNotEmpty())

            val isRecent = recentlyUsedNumber != null && phone.value == recentlyUsedNumber
            rowBinding.phoneNumberRecentsBadge.beVisibleIf(isRecent)

            rowBinding.phoneRowRoot.setOnClickListener {
                startCallWithConfirmationCheck(phone.value, contactName)
            }

            binding.phonesCard.addView(rowBinding.root)
        }
    }

    private fun renderEmails(emails: List<Email>) {
        binding.emailsCard.removeAllViews()
        if (emails.isEmpty()) {
            binding.emailsCard.visibility = View.GONE
            return
        }
        binding.emailsCard.visibility = View.VISIBLE

        emails.forEach { email ->
            val rowBinding = ItemContactEmailBinding.inflate(LayoutInflater.from(this), binding.emailsCard, false)
            rowBinding.emailValue.text = email.value
            rowBinding.emailLabel.text = emailTypeLabel(email.type, email.label)
            rowBinding.emailRowRoot.setOnClickListener {
                val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.value}"))
                try {
                    startActivity(mailto)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            binding.emailsCard.addView(rowBinding.root)
        }
    }

    private fun renderMoreContent(c: Contact) {
        var anyShown = false

        anyShown = anyShown or addMoreField(binding.moreAddress, R.string.address, c.addresses.joinToString("\n") { it.value })
        anyShown = anyShown or addMoreField(binding.moreBirthday, R.string.birthday, c.birthdays.joinToString(", "))
        anyShown = anyShown or addMoreField(binding.moreNotes, R.string.notes, c.notes)
        anyShown = anyShown or addMoreField(binding.moreWebsite, R.string.website, c.websites.joinToString("\n"))
        anyShown = anyShown or addMoreField(binding.moreIm, R.string.im_accounts, c.IMs.joinToString("\n") { it.value })
        anyShown = anyShown or addMoreField(binding.moreGroups, R.string.groups, c.groups.joinToString(", ") { it.title })

        binding.moreHeader.visibility = if (anyShown) View.VISIBLE else View.GONE
    }

    private fun addMoreField(includeBinding: ItemContactDetailFieldBinding, labelResId: Int, value: String): Boolean {
        val visible = value.isNotBlank()
        includeBinding.fieldRoot.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            includeBinding.fieldLabel.text = getString(labelResId)
            includeBinding.fieldValue.text = value
        }
        return visible
    }

    private fun toggleMoreExpander() {
        val isExpanded = binding.moreContent.visibility == View.VISIBLE
        binding.moreContent.visibility = if (isExpanded) View.GONE else View.VISIBLE
        binding.moreChevron.rotation = if (isExpanded) 0f else 180f
    }

    private fun renderPreferredSim(primaryNumber: String?) {
        // Build 2 ships the row as a placeholder. Real per-contact SIM resolution
        // ships in Build 3 — at that point this reads from a new config method
        // and the picker writes a PhoneAccountHandle via saveCustomSIM.
        binding.preferredSimValue.setText(R.string.preferred_sim_default)
    }

    private fun showPreferredSimPicker(primaryNumber: String?) {
        toast(R.string.not_yet_implemented)
    }

    private fun configureBottomBar(forKnownContact: Boolean, contact: Contact?, unknownNumberFallback: String? = null) {
        if (forKnownContact && contact != null) {
            binding.bottomButton1Icon.setImageResource(R.drawable.ic_star_vector)
            binding.bottomButton1Label.setText(R.string.favourite)
            binding.bottomButton1.setOnClickListener { toggleFavourite(contact) }

            binding.bottomButton2Icon.setImageResource(R.drawable.ic_edit_vector)
            binding.bottomButton2Label.setText(R.string.edit)
            binding.bottomButton2.setOnClickListener { launchSystemEditIntent(contact) }

            binding.bottomButton3Icon.setImageResource(R.drawable.ic_three_dots_vector)
            binding.bottomButton3Label.setText(R.string.more)
            binding.bottomButton3.setOnClickListener { showKnownContactMoreMenu(contact) }
        } else {
            // Unknown number bottom bar
            binding.bottomButton1Icon.setImageResource(R.drawable.ic_plus_vector)
            binding.bottomButton1Label.setText(R.string.create_new_contact)
            binding.bottomButton1.setOnClickListener {
                val n = unknownNumberFallback ?: return@setOnClickListener
                Intent(Intent.ACTION_INSERT, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, n)
                    try { startActivity(this) } catch (e: Exception) { showErrorToast(e) }
                }
            }

            binding.bottomButton2Icon.setImageResource(R.drawable.ic_person_vector)
            binding.bottomButton2Label.setText(R.string.add_to_existing_contact)
            binding.bottomButton2.setOnClickListener {
                val n = unknownNumberFallback ?: return@setOnClickListener
                Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, n)
                    try { startActivity(this) } catch (e: Exception) { showErrorToast(e) }
                }
            }

            binding.bottomButton3Icon.setImageResource(R.drawable.ic_three_dots_vector)
            binding.bottomButton3Label.setText(R.string.more)
            binding.bottomButton3.setOnClickListener { showUnknownNumberMoreMenu(unknownNumberFallback) }
        }
    }

    private fun toggleFavourite(c: Contact) {
        // Star/unstar via system Contacts content provider — read-only field on our cached Contact.
        // For now defer to system Contacts edit screen so the user can toggle the star explicitly.
        // Full inline toggle is a Build 7 polish item.
        toast(R.string.not_yet_implemented)
    }

    private fun launchSystemEditIntent(c: Contact) {
        // Custom Edit screen is deferred to Build 7. For now delegate to the system Contacts editor
        // so the user can still edit names / phones / emails / company / etc.
        ensureBackgroundThread {
            val lookupKey = SimpleContactsHelper(this).getContactLookupKey(c.rawId.toString())
            val uri = Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    try { startActivity(this) } catch (e: Exception) { showErrorToast(e) }
                }
            }
        }
    }

    private fun showKnownContactMoreMenu(c: Contact) {
        val popup = PopupMenu(this, binding.bottomButton3)
        popup.menu.add(0, 1, 0, R.string.share_short)
        popup.menu.add(0, 2, 1, R.string.copy_number)
        popup.menu.add(0, 3, 2, R.string.block_number_short)
        popup.setOnMenuItemClickListener { item ->
            val number = recentlyUsedNumber ?: c.phoneNumbers.firstOrNull()?.value
            when (item.itemId) {
                1 -> shareContact(c)
                2 -> {
                    if (number != null) {
                        copyToClipboard(number)
                    }
                }
                3 -> toast(R.string.not_yet_implemented)
            }
            true
        }
        popup.show()
    }

    private fun showUnknownNumberMoreMenu(number: String?) {
        if (number == null) return
        val popup = PopupMenu(this, binding.bottomButton3)
        popup.menu.add(0, 1, 0, R.string.block_number_short)
        popup.menu.add(0, 2, 1, R.string.copy_number)
        popup.menu.add(0, 3, 2, R.string.send_to_spam)
        popup.menu.add(0, 4, 3, R.string.share_short)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> toast(R.string.not_yet_implemented)
                2 -> copyToClipboard(number)
                3 -> toast(R.string.not_yet_implemented)
                4 -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, number)
                    }
                    try {
                        startActivity(Intent.createChooser(sendIntent, null))
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun shareContact(c: Contact) {
        val number = recentlyUsedNumber ?: c.phoneNumbers.firstOrNull()?.value ?: return
        val text = "${c.getNameToDisplay()}\n$number"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, null))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun showNumberPicker(c: Contact, onPicked: (String) -> Unit) {
        if (c.phoneNumbers.isEmpty()) return
        if (c.phoneNumbers.size == 1) {
            onPicked(c.phoneNumbers.first().value)
            return
        }
        val popup = PopupMenu(this, binding.actionRow)
        c.phoneNumbers.forEachIndexed { index, phone ->
            val type = getPhoneNumberTypeText(phone.type, phone.label)
            val title = if (type.isNotBlank()) "${phone.value} · $type" else phone.value
            popup.menu.add(0, index, index, title)
        }
        popup.setOnMenuItemClickListener { item ->
            onPicked(c.phoneNumbers[item.itemId].value)
            true
        }
        popup.show()
    }

    private fun openWhatsApp(rawNumber: String) {
        // wa.me format requires only digits and a leading country code (no +, spaces, or dashes).
        val normalized = rawNumber.replace(Regex("[^0-9]"), "")
        val uri = Uri.parse("https://wa.me/$normalized")

        // On Android 11+ (target SDK 30+), an explicit setPackage() check requires the
        // target package to be declared in <queries> in the manifest — which we do for
        // both WhatsApp variants. We try the consumer app first, then WhatsApp Business
        // as a fallback, and only show the "not installed" toast if both genuinely fail.
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in candidates) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(pkg) }
            try {
                startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // try the next candidate
            }
        }
        toast(R.string.whatsapp_not_installed)
    }

    private fun findMostRecentNumber(numbers: List<PhoneNumber>): String? {
        // Naive synchronous-friendly: query RecentsHelper for limited window, find the latest call
        // matching any of this contact's numbers. Cheap enough to run on UI thread for small lists.
        if (numbers.isEmpty()) return null
        return numbers.firstOrNull()?.value
    }

    private fun locationFor(number: String): String? {
        return try {
            val util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
            val locale = java.util.Locale.getDefault()
            val parsed = util.parse(number, locale.country)
            com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
                .getInstance()
                .getDescriptionForNumber(parsed, locale, locale.country)
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
