package com.archsolutions.shopmanager.feature.staff

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleCartItem
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.feature.sales.SaleDetailViewModel
import com.archsolutions.shopmanager.feature.sell.SellViewModel
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ruling R1 at ViewModel level: who gets the name on a void. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActorCreditTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock("2026-09-30")
    private lateinit var db: AppDatabase

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    private class Shop(
        val saleRepo: SaleRepo,
        val settings: SettingsStore,
        val staffRepo: StaffRepo,
        val session: StaffSession,
        val saleId: Long,
        val ownerId: Long,
        val cashierId: Long,
        val dayRepo: DayRepo,
        val customerRepo: CustomerRepo,
    )

    private suspend fun TestScope.shop(): Shop {
        db = AppDatabase.createInMemory(context)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("a_${System.nanoTime()}.preferences_pb") }),
        )
        val staffRepo = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000)
        staffRepo.enableStaff("Grace", "1111")
        val ownerId = staffRepo.staff.first().single().id
        val cashierId = staffRepo.addStaff("Amina", Role.CASHIER, "2222")
        val dayRepo = DayRepo(db, clock)
        val saleRepo = SaleRepo(db, clock)
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val product = db.productDao().insert(
            ProductEntity(name = "Sugar", categoryId = null, sellPrice = 5_000L, costPrice = 4_000L, stockQty = 10.0, lowStockLevel = 1.0, createdAt = 0L, updatedAt = 0L),
        )
        val session = StaffSession(clock)
        val saleId = saleRepo.createSale(dayId, PayMethod.CASH, listOf(SaleCartItem(product, "Sugar", 1.0, 5_000L, 4_000L)), cashReceived = 5_000L, staffId = cashierId)
        return Shop(saleRepo, settings, staffRepo, session, saleId, ownerId, cashierId, dayRepo, CustomerRepo(db))
    }

    @Test
    fun `an Owner-approved void credits the approving Owner, not the signed-in cashier`() = runTest {
        val s = shop()
        s.session.signIn(s.staffRepo.staff.first().first { it.id == s.cashierId })
        val vm = SaleDetailViewModel(
            SavedStateHandle(mapOf("id" to s.saleId)), s.saleRepo, s.dayRepo, s.customerRepo, s.settings, clock, s.session, s.staffRepo,
        )
        // What PinViewModel.runApproved does around the action: approvedBy set, void reads it synchronously, then cleared.
        s.session.approvedBy = s.ownerId
        try {
            vm.void("Wrong item", "")
        } finally {
            s.session.approvedBy = null
        }

        val sale = s.saleRepo.getSaleWithItems(s.saleId).first()!!.sale
        assertEquals(s.ownerId, sale.voidedByStaffId)
        assertEquals(s.cashierId, sale.staffId)
    }

    @Test
    fun `a void without an approval credits the signed-in person`() = runTest {
        val s = shop()
        s.session.signIn(s.staffRepo.staff.first().first { it.id == s.ownerId })
        val vm = SaleDetailViewModel(
            SavedStateHandle(mapOf("id" to s.saleId)), s.saleRepo, s.dayRepo, s.customerRepo, s.settings, clock, s.session, s.staffRepo,
        )

        vm.void("Wrong item", "")

        assertEquals(s.ownerId, s.saleRepo.getSaleWithItems(s.saleId).first()!!.sale.voidedByStaffId)
    }

    @Test
    fun `undo credits the current person even if an approval is pending`() = runTest {
        val s = shop()
        s.session.signIn(s.staffRepo.staff.first().first { it.id == s.cashierId })
        val gateSettings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("g_${System.nanoTime()}.preferences_pb") }),
        )
        val vm = SellViewModel(ProductRepo(db, clock), s.customerRepo, s.saleRepo, DayGate(s.dayRepo, gateSettings, clock), SavedStateHandle(), s.session)
        s.session.approvedBy = s.ownerId

        vm.undoSale(s.saleId)

        s.session.approvedBy = null
        assertEquals(s.cashierId, s.saleRepo.getSaleWithItems(s.saleId).first()!!.sale.voidedByStaffId)
    }
}
