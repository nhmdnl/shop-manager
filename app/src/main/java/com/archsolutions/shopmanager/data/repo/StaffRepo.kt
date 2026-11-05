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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Staff list, per-person PINs and Owner recovery. The legacy shared PIN setting is never touched here. */
@Singleton
class StaffRepo(
    private val db: AppDatabase,
    private val staffDao: StaffDao,
    private val settings: SettingsStore,
    private val clock: AppClock,
    private val pinIterations: Int,
) {
    @Inject
    constructor(db: AppDatabase, staffDao: StaffDao, settings: SettingsStore, clock: AppClock) :
        this(db, staffDao, settings, clock, PinHasher.ITERATIONS)

    val staff: Flow<List<StaffEntity>> = staffDao.getAll()

    /** Turns staff on: creates the first Owner, or reuses the existing one after an off-then-on. Throws if already on. Returns the recovery code to show once. */
    suspend fun enableStaff(ownerName: String, ownerPin: String): String {
        val name = cleanName(ownerName)
        val code = RecoveryCode.generate()
        db.withTransaction {
            check(!settings.settings.first().staffEnabled) { "Staff is already on" }
            val owner = staffDao.getAll().first().filter { it.role == Role.OWNER.key }.minByOrNull { it.id }
            if (owner != null) {
                // Off-then-on: reuse the first Owner row so the old PIN stops working and no duplicate appears.
                staffDao.update(owner.copy(name = name, pinHash = hash(ownerPin), active = true))
            } else {
                staffDao.insert(
                    StaffEntity(name = name, role = Role.OWNER.key, pinHash = hash(ownerPin), createdAt = clock.nowMillis()),
                )
            }
            settings.setRecoveryHash(hash(RecoveryCode.normalize(code)))
            settings.setStaffEnabled(true)
        }
        return code
    }

    /** Staff rows stay, so turning staff on again finds them. */
    suspend fun disableStaff() {
        settings.setStaffEnabled(false)
    }

    suspend fun addStaff(name: String, role: Role, pin: String): Long =
        staffDao.insert(StaffEntity(name = cleanName(name), role = role.key, pinHash = hash(pin), createdAt = clock.nowMillis()))

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
        return PinHasher.matches(pin, person.pinHash)
    }

    /** Sets a new PIN for the Owner when [code] is right; returns the fresh recovery code, or null when wrong. */
    suspend fun recoverOwner(ownerId: Long, code: String, newPin: String): String? {
        val owner = staffDao.getByIdDirect(ownerId)?.takeIf { it.active && it.role == Role.OWNER.key } ?: return null
        val stored = settings.settings.first().recoveryHash
        if (!PinHasher.matches(RecoveryCode.normalize(code), stored)) return null
        val fresh = RecoveryCode.generate()
        db.withTransaction {
            staffDao.update(owner.copy(pinHash = hash(newPin)))
            settings.setRecoveryHash(hash(RecoveryCode.normalize(fresh)))
        }
        return fresh
    }

    /** A fresh recovery code for an active Owner (the old one stops working); null for anyone else. Returned plain once, stored hashed. */
    suspend fun regenerateRecoveryCode(ownerId: Long): String? {
        val owner = staffDao.getByIdDirect(ownerId)?.takeIf { it.active && it.role == Role.OWNER.key } ?: return null
        val fresh = RecoveryCode.generate()
        db.withTransaction { settings.setRecoveryHash(hash(RecoveryCode.normalize(fresh))) }
        return fresh
    }

    private fun hash(secret: String) = PinHasher.hash(secret, pinIterations)

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }
}
