package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.security.PinHasher
import com.archsolutions.shopmanager.core.security.RecoveryCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.StaffDao
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Re-enabling staff needs the existing Owner's current PIN, and this one was wrong. */
class WrongOwnerPinException : RuntimeException("The Owner PIN does not match")

/**
 * Staff list, per-person PINs and Owner recovery. The legacy shared PIN setting is never touched here.
 * Every PBKDF2 hash or check runs on [hashDispatcher] (120k iterations is too slow for the Main thread).
 */
@Singleton
class StaffRepo(
    private val db: AppDatabase,
    private val staffDao: StaffDao,
    private val settings: SettingsStore,
    private val clock: AppClock,
    private val pinIterations: Int,
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(db: AppDatabase, staffDao: StaffDao, settings: SettingsStore, clock: AppClock) :
        this(db, staffDao, settings, clock, PinHasher.ITERATIONS, Dispatchers.Default)

    val staff: Flow<List<StaffEntity>> = staffDao.getAll()

    /**
     * Staff counts as on only when the setting is on AND an active Owner exists. If the two ever disagree (a failed
     * write, a restored backup) the shop behaves as staff off rather than locking everyone out.
     */
    val staffActive: Flow<Boolean> = combine(settings.settings, staff) { s, all ->
        s.staffEnabled && all.any { it.active && it.role == Role.OWNER.key }
    }.distinctUntilChanged()

    /** The Owner row that setting staff up again would use (first active Owner, else the first Owner), or null on a first set-up. */
    suspend fun existingOwner(): StaffEntity? {
        val owners = staffDao.getAll().first().filter { it.role == Role.OWNER.key }
        return owners.filter { it.active }.minByOrNull { it.id } ?: owners.minByOrNull { it.id }
    }

    /**
     * Turns staff on and returns the recovery code to show once. First set-up creates the Owner [ownerName] with
     * [ownerPin]. When an Owner row already exists (off-then-on) [ownerPin] must be THAT Owner's current PIN
     * ([WrongOwnerPinException] otherwise); the Owner keeps name and PIN and nothing is inserted. Throws if already on.
     *
     * Order matters (the shop must never be left with staff on and no way in): the database change commits first, then
     * the recovery hash, and the on flag is written last. [beforeEnable] runs with the Owner just before the flag flips.
     */
    suspend fun enableStaff(ownerName: String, ownerPin: String, beforeEnable: suspend (StaffEntity) -> Unit = {}): String {
        check(!staffActive.first()) { "Staff is already on" } // flag AND an active Owner; the flag alone would block recovering a shop with no Owner
        val existing = existingOwner()
        val owner: StaffEntity
        if (existing != null) {
            if (!matches(ownerPin, existing.pinHash)) throw WrongOwnerPinException()
            owner = if (existing.active) existing else existing.copy(active = true).also { staffDao.update(it) }
        } else {
            val name = cleanName(ownerName)
            val entity = StaffEntity(name = name, role = Role.OWNER.key, pinHash = hash(ownerPin), createdAt = clock.nowMillis())
            owner = entity.copy(id = db.withTransaction { staffDao.insert(entity) })
        }
        val code = RecoveryCode.generate()
        settings.setRecoveryHash(hash(RecoveryCode.normalize(code)))
        beforeEnable(owner)
        settings.setStaffEnabled(true)
        return code
    }

    /** Staff rows stay, so turning staff on again finds them. */
    suspend fun disableStaff() {
        settings.setStaffEnabled(false)
    }

    suspend fun addStaff(name: String, role: Role, pin: String): Long {
        val clean = cleanName(name)
        return staffDao.insert(StaffEntity(name = clean, role = role.key, pinHash = hash(pin), createdAt = clock.nowMillis()))
    }

    suspend fun rename(id: Long, name: String) {
        val person = staffDao.getByIdDirect(id) ?: return
        staffDao.update(person.copy(name = cleanName(name)))
    }

    /** False (and nothing changes) when this would deactivate the last active Owner. */
    suspend fun setActive(id: Long, active: Boolean): Boolean = db.withTransaction {
        val person = staffDao.getByIdDirect(id) ?: return@withTransaction false
        if (!active && person.active && person.role == Role.OWNER.key && staffDao.activeOwnerCount() <= 1) {
            return@withTransaction false
        }
        staffDao.update(person.copy(active = active))
        true
    }

    suspend fun resetPin(id: Long, newPin: String) {
        val person = staffDao.getByIdDirect(id) ?: return
        staffDao.update(person.copy(pinHash = hash(newPin)))
    }

    suspend fun verify(id: Long, pin: String): Boolean {
        val person = staffDao.getByIdDirect(id)?.takeIf { it.active } ?: return false
        return matches(pin, person.pinHash)
    }

    /** Sets a new PIN for the Owner when [code] is right; returns the fresh recovery code, or null when wrong. */
    suspend fun recoverOwner(ownerId: Long, code: String, newPin: String): String? {
        val owner = staffDao.getByIdDirect(ownerId)?.takeIf { it.active && it.role == Role.OWNER.key } ?: return null
        val stored = settings.settings.first().recoveryHash
        if (!matches(RecoveryCode.normalize(code), stored)) return null
        val fresh = RecoveryCode.generate()
        val newHash = hash(newPin)
        val freshHash = hash(RecoveryCode.normalize(fresh))
        // Database first, then the recovery hash: a failure in between leaves the old code working, never a code nobody saw.
        db.withTransaction { staffDao.update(owner.copy(pinHash = newHash)) }
        settings.setRecoveryHash(freshHash)
        return fresh
    }

    /** A fresh recovery code for an active Owner (the old one stops working); null for anyone else. Returned plain once, stored hashed. */
    suspend fun regenerateRecoveryCode(ownerId: Long): String? {
        staffDao.getByIdDirect(ownerId)?.takeIf { it.active && it.role == Role.OWNER.key } ?: return null
        val fresh = RecoveryCode.generate()
        settings.setRecoveryHash(hash(RecoveryCode.normalize(fresh)))
        return fresh
    }

    private suspend fun hash(secret: String): String = withContext(hashDispatcher) { PinHasher.hash(secret, pinIterations) }

    private suspend fun matches(secret: String, stored: String): Boolean = withContext(hashDispatcher) { PinHasher.matches(secret, stored) }

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }
}
