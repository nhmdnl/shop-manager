package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import android.os.Build
import com.archsolutions.cloud.contract.CONSENT_VERSION
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.cloud.contract.LinkRequest
import com.archsolutions.cloud.contract.ShopStatus
import com.archsolutions.cloud.contract.VerifyResponse
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CloudState {
    /** No cloud URL in this build: every cloud screen stays hidden and nothing touches the network. */
    data object Off : CloudState
    data object SignedOut : CloudState
    /** Another tablet took over this shop. */
    data object Replaced : CloudState
    data class Linked(val status: ShopStatus?, val lastCloudBackupAt: Long?) : CloudState
}

sealed interface LinkOutcome {
    data object Linked : LinkOutcome
    data object TabletExists : LinkOutcome
}

/** What the sign-in screens need from the cloud link; a fake in their tests. */
interface CloudAccount {
    suspend fun requestCode(phone: String)
    suspend fun verify(phone: String, code: String): VerifyResponse
    suspend fun link(ticket: String, phone: String, replace: Boolean, ownerName: String?, consent: Boolean): LinkOutcome
}

@Singleton
class CloudRepo @Inject constructor(
    private val store: CloudStore,
    private val api: CloudApi?,
    private val settings: SettingsStore,
    private val clock: AppClock,
    @param:ApplicationContext private val context: Context,
) : CloudAccount {

    val state: Flow<CloudState> = if (api == null) {
        flowOf(CloudState.Off)
    } else {
        store.snapshot.map { s ->
            when {
                s.linked -> CloudState.Linked(s.status, s.lastCloudBackupAt)
                s.replaced -> CloudState.Replaced
                else -> CloudState.SignedOut
            }
        }
    }

    override suspend fun requestCode(phone: String) = call { it.requestCode(phone) }

    override suspend fun verify(phone: String, code: String): VerifyResponse = call { it.verify(phone, code) }

    /** A broken Keystore makes [CloudStore.saveLink] throw; that reaches the caller on purpose. */
    override suspend fun link(
        ticket: String,
        phone: String,
        replace: Boolean,
        ownerName: String?,
        consent: Boolean,
    ): LinkOutcome {
        val s = settings.settings.first()
        val request = LinkRequest(
            ownerTicket = ticket,
            label = Build.MODEL,
            shopName = s.shopName.ifBlank { context.getString(R.string.cloud_default_shop_name) },
            language = s.language,
            replace = replace,
            ownerName = ownerName,
            consentVersion = if (consent) CONSENT_VERSION else null,
        )
        val response = try {
            call { it.link(request) }
        } catch (e: CloudException) {
            if (e.code == ErrorCode.TABLET_EXISTS || e.http == 409) return LinkOutcome.TabletExists
            throw e
        }
        store.saveLink(response.deviceToken, response.shopId, phone)
        return LinkOutcome.Linked
    }

    /** Tells the server (best effort: offline is fine), then forgets the link on this tablet. */
    suspend fun unlink() {
        if (api != null) {
            try {
                call { it.unlink() }
            } catch (_: IOException) {
            } catch (_: CloudException) {
            }
        }
        store.clearLink()
    }

    /** Fetches the plan status and caches it for the card; null when offline, refused or Off. */
    suspend fun refreshStatus(): ShopStatus? {
        if (api == null) return null
        return try {
            call { it.status() }.also { store.saveStatus(it) }
        } catch (_: IOException) {
            null
        } catch (_: CloudException) {
            null
        }
    }

    /**
     * The one place a server error changes the link: DEVICE_REVOKED means another tablet replaced this one,
     * UNAUTHORIZED means the token is no longer good. The exception is rethrown either way.
     */
    suspend fun <T> call(block: suspend (CloudApi) -> T): T {
        val api = checkNotNull(api) { "Cloud is off in this build" }
        try {
            return block(api)
        } catch (e: CloudException) {
            when (e.code) {
                ErrorCode.DEVICE_REVOKED -> store.markReplaced()
                ErrorCode.UNAUTHORIZED -> store.clearLink()
                else -> Unit
            }
            throw e
        }
    }
}
