package org.fossify.phone.extensions

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.activities.ContactDetailActivity
import org.fossify.phone.activities.SimpleActivity

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> startCallWithConfirmationCheck(contact)
        ON_CLICK_VIEW_CONTACT -> startContactDetailsIntent(contact)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

// Build 2: every entry point that previously delegated to the system Contacts viewer now
// opens our custom ContactDetailActivity. The phone-number argument lets the new screen
// highlight the most-recently-used number with a "Recents" badge.
fun Activity.startContactDetailsIntent(contact: Contact, phoneNumber: String? = null) {
    if (this is SimpleActivity) {
        ContactDetailActivity.launchKnownContact(this, contact.contactId, phoneNumber)
    } else {
        // Fallback for non-SimpleActivity callers (rare). Build the intent directly.
        val intent = Intent(this, ContactDetailActivity::class.java).apply {
            putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contact.contactId)
            if (phoneNumber != null) putExtra(ContactDetailActivity.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        startActivity(intent)
    }
}
