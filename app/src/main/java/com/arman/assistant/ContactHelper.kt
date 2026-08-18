package com.arman.assistant

import android.content.Context
import android.provider.ContactsContract

/**
 * Looks up a phone number by contact name using the device's contacts list.
 * Requires READ_CONTACTS permission to be granted before calling.
 */
object ContactHelper {

    fun findNumber(context: Context, name: String): String? {
        val query = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(query, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(nameIndex) ?: continue
                if (contactName.contains(name, ignoreCase = true)) {
                    return cursor.getString(numberIndex)
                }
            }
        }
        return null
    }
}
