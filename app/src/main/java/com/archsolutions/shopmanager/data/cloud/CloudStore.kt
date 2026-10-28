package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archsolutions.cloud.contract.ArchJson
import com.archsolutions.cloud.contract.ShopStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.util.Base64

/**
 * The cloud DataStore: its own file (`files/datastore/cloud.preferences_pb`), apart from the shop settings,
 * so [com.archsolutions.shopmanager.data.backup.BackupManager] never zips the link and a restore can't
 * carry one phone's token onto another.
 */
val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud")

data class CloudSnapshot(
    val linked: Boolean,
    val replaced: Boolean,
    val phone: String?,
    val shopId: String?,
    val status: ShopStatus?,
    val lastCloudBackupAt: Long?,
)

class CloudStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) {
    /** `linked` means a token is stored; only [token] finds out whether it can still be opened. */
    val snapshot: Flow<CloudSnapshot> = dataStore.data.map { p ->
        CloudSnapshot(
            linked = p[TOKEN] != null,
            replaced = p[REPLACED] ?: false,
            phone = p[PHONE],
            shopId = p[SHOP_ID],
            status = p[STATUS]?.let { runCatching { ArchJson.decodeFromString<ShopStatus>(it) }.getOrNull() },
            lastCloudBackupAt = p[LAST_BACKUP],
        )
    }

    suspend fun saveLink(token: String, shopId: String, phone: String) {
        val sealed = Base64.encodeToString(cipher.encrypt(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        dataStore.edit {
            it[TOKEN] = sealed
            it[SHOP_ID] = shopId
            it[PHONE] = phone
            it[REPLACED] = false
        }
    }

    /** The device token, or null when none is stored or it can't be decrypted (lost key): that signs out. */
    suspend fun token(): String? {
        val sealed = dataStore.data.first()[TOKEN] ?: return null
        val plain = runCatching { Base64.decode(sealed, Base64.NO_WRAP) }.getOrNull()?.let(cipher::decrypt)
        if (plain == null) {
            clearLink()
            return null
        }
        return String(plain, Charsets.UTF_8)
    }

    suspend fun clearLink() {
        dataStore.edit {
            it.remove(TOKEN)
            it.remove(SHOP_ID)
            it.remove(PHONE)
            it.remove(STATUS)
            it.remove(REPLACED)
        }
    }

    /** Another phone took over this shop: drop the token and remember why. */
    suspend fun markReplaced() {
        dataStore.edit {
            it.remove(TOKEN)
            it[REPLACED] = true
        }
    }

    suspend fun saveStatus(status: ShopStatus) {
        dataStore.edit { it[STATUS] = ArchJson.encodeToString(ShopStatus.serializer(), status) }
    }

    suspend fun setLastCloudBackup(at: Long) {
        dataStore.edit { it[LAST_BACKUP] = at }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val SHOP_ID = stringPreferencesKey("shop_id")
        val PHONE = stringPreferencesKey("phone")
        val STATUS = stringPreferencesKey("status")
        val REPLACED = booleanPreferencesKey("replaced")
        val LAST_BACKUP = longPreferencesKey("last_cloud_backup")
    }
}
