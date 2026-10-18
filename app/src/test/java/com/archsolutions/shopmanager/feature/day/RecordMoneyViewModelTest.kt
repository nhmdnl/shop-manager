package com.archsolutions.shopmanager.feature.day

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordMoneyViewModelTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dayRepo: DayRepo
    private lateinit var moneyRepo: MoneyRepo
    private lateinit var customerRepo: CustomerRepo
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
        dayRepo = DayRepo(db, clock)
        moneyRepo = MoneyRepo(db, clock)
        customerRepo = CustomerRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(handle: SavedStateHandle = SavedStateHandle()): RecordMoneyViewModel {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("money_${System.nanoTime()}.preferences_pb") },
            )
        )
        val viewModel = RecordMoneyViewModel(moneyRepo, customerRepo, SaleRepo(db, clock), DayGate(dayRepo, settingsStore, clock), handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `expense is saved with its category key`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 0L)
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.setAmount(10_000L)
        viewModel.setCategory(ExpenseCategory.TRANSPORT)
        viewModel.setNote("Boda to City Wholesale")
        viewModel.state.first { it.amount == 10_000L && it.note.isNotEmpty() }
        viewModel.save()
        viewModel.saved.first { it != null }

        val entry = moneyRepo.getEntriesForDayDirect(dayId).single()
        assertEquals(MoneyEntryType.EXPENSE, entry.type)
        assertEquals("Transport", entry.category)
        assertEquals(10_000L, entry.amount)
        assertEquals("Boda to City Wholesale", entry.note)
    }

    @Test
    fun `paying back more than is owed needs a confirmation and leaves credit`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L)
        val joseph = customerRepo.saveCustomer("Joseph K.")
        db.saleDao().insertSale(
            SaleEntity(dayId = dayId, createdAt = 0L, total = 12_000L, costTotal = 9_000L, method = PayMethod.CREDIT, customerId = joseph)
        )
        val viewModel = newViewModel(SavedStateHandle(mapOf("type" to "CREDIT_REPAYMENT", "customerId" to joseph)))
        val ready = viewModel.state.first { it.selectedCustomer != null }
        assertEquals(MoneyEntryType.CREDIT_REPAYMENT, ready.type)
        assertEquals(12_000L, ready.selectedCustomer!!.owed)

        viewModel.setAmount(15_000L)
        viewModel.setMethod(MoneyMethod.MOBILE_MONEY)
        assertEquals(-3_000L, viewModel.state.first { it.amount == 15_000L }.owedAfter)
        viewModel.save()

        assertTrue(viewModel.confirmOverpayment.value)
        assertTrue(moneyRepo.getEntriesForDayDirect(dayId).isEmpty())

        viewModel.confirmOverpayment()
        viewModel.saved.first { it != null }

        val entry = moneyRepo.getEntriesForDayDirect(dayId).single()
        assertEquals(MoneyEntryType.CREDIT_REPAYMENT, entry.type)
        assertEquals(MoneyMethod.MOBILE_MONEY, entry.method)
        assertEquals(-3_000L, customerRepo.getCustomerBalanceOwedDirect(joseph))
    }
}
