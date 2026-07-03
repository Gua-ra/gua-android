/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import android.content.Context
import android.provider.ContactsContract
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * GUA FORK: reads the device address book and returns a map of normalized E.164 number -> best local
 * display name. Android counterpart of iOS `ContactDiscoveryService.readAddressBook`.
 *
 * PRIVACY: this is a one-shot read used only to build the lookup payload. The address book is never
 * persisted, and individual phone numbers are never logged.
 */
interface ContactsReader {
    /** @return E.164 number -> the best local name for it, or an empty map if none/unreadable. */
    fun readContacts(): Map<String, String>
}

@ContributesBinding(AppScope::class)
class AndroidContactsReader(
    @ApplicationContext private val context: Context,
) : ContactsReader {
    override fun readContacts(): Map<String, String> {
        val defaultDialCode = DeviceDialCode.resolve(context)
        val nameByNumber = mutableMapOf<String, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                if (numberIndex < 0) return emptyMap()
                while (cursor.moveToNext()) {
                    val rawNumber = cursor.getString(numberIndex)
                    val e164 = rawNumber?.let { PhoneNumberNormalizer.normalize(it, defaultDialCode) }
                    // First non-empty name wins; never overwrite a real name with a blank.
                    if (e164 != null && nameByNumber[e164].isNullOrEmpty()) {
                        val name = (if (nameIndex >= 0) cursor.getString(nameIndex) else null).orEmpty()
                        nameByNumber[e164] = name.ifEmpty { e164 }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the read; treat as no contacts.
            Timber.w(e, "Contacts read denied")
            return emptyMap()
        } catch (e: Exception) {
            Timber.w(e, "Failed to read contacts")
            return emptyMap()
        }

        return nameByNumber
    }
}
