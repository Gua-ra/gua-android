/*
 * Copyright 2026 Gua
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

interface ContactsReader {
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
