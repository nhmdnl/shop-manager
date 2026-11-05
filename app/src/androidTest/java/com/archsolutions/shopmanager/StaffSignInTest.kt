package com.archsolutions.shopmanager

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.window.Dialog
import com.archsolutions.shopmanager.domain.staff.SignInGate
import com.archsolutions.shopmanager.feature.staff.LocalSignOutTick
import com.archsolutions.shopmanager.feature.staff.OnSignOut
import com.archsolutions.shopmanager.feature.staff.StaffGateLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffGuard
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.domain.staff.shouldShowSignIn
import com.archsolutions.shopmanager.feature.staff.StaffSignInScreen
import com.archsolutions.shopmanager.feature.staff.StaffSignInViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The sign-in screen with the real view model, guard and session on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class StaffSignInTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val queryExecutor = Executors.newFixedThreadPool(4)
    private val db = AppDatabase.configure(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
        .allowMainThreadQueries()
        .setQueryExecutor(queryExecutor)
        .build()
    private val viewModelStore = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = object : AppClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
        override fun todayDateString(): String = "2026-09-30"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }
    private val session = StaffSession(clock)
    private var cashierId = 0L

    @After
    fun tearDown() {
        viewModelStore.clear()
        scope.cancel()
        queryExecutor.shutdown()
        queryExecutor.awaitTermination(5, TimeUnit.SECONDS)
        db.close()
    }

    /** Owner Grace (1111), cashier Amina (2222), and cashier Peter (3333) who is switched off. */
    private fun staffShop(): StaffSignInViewModel = runBlocking {
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.cacheDir, "signin_${System.nanoTime()}.preferences_pb") }))
        val repo = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000)
        repo.enableStaff("Grace", "1111")
        cashierId = repo.addStaff("Amina", Role.CASHIER, "2222")
        val peter = repo.addStaff("Peter", Role.CASHIER, "3333")
        repo.setActive(peter, false)
        val guard = StaffGuard(repo, session, clock)
        val factory = viewModelFactory { initializer { StaffSignInViewModel(repo, guard) } }
        ViewModelProvider(viewModelStore, factory)[StaffSignInViewModel::class.java]
    }

    private fun tapPin(pin: String) = pin.forEach { composeRule.onNodeWithText(it.toString()).performClick() }

    @Test
    fun signInScreenListsActiveStaffOnly() {
        val viewModel = staffShop()
        composeRule.setContent { ShopManagerTheme(themeMode = ThemeMode.LIGHT) { StaffSignInScreen(viewModel) } }

        composeRule.onNodeWithText("Grace").assertIsDisplayed()
        composeRule.onNodeWithText("Amina").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Peter").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun rightPinLeavesTheScreenAndTheSessionHoldsThePerson() {
        val viewModel = staffShop()
        composeRule.setContent {
            val current by session.current.collectAsState()
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                if (shouldShowSignIn(staffEnabled = true, signedIn = current != null, onboarding = false)) StaffSignInScreen(viewModel)
            }
        }

        composeRule.onNodeWithText("Amina").performClick()
        tapPin("2222")

        composeRule.waitUntil(timeoutMillis = 5_000) { session.currentId == cashierId }
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Amina").fetchSemanticsNodes().isEmpty() }
        assertEquals("Amina", session.current.value?.name)
    }

    @Test
    fun wrongPinShowsTriesLeft() {
        val viewModel = staffShop()
        composeRule.setContent { ShopManagerTheme(themeMode = ThemeMode.LIGHT) { StaffSignInScreen(viewModel) } }

        composeRule.onNodeWithText("Amina").performClick()
        tapPin("9999")

        val expected = context.resources.getQuantityString(R.plurals.plurals_pin_tries_left, 4, 4)
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(null, session.currentId)
    }

    @Test
    fun signInScreenDoesNotClipAt200PercentFont() {
        val viewModel = staffShop()
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredWidth(390.dp).fillMaxHeight().testTag(PHONE_TAG)) { StaffSignInScreen(viewModel) }
                    }
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.staff_signin_title)).assertIsDisplayed()
        assertNothingClipped()

        composeRule.onNodeWithText("Amina").performClick()
        composeRule.onNodeWithText(context.getString(R.string.staff_signin_pin_hint)).assertIsDisplayed()
        assertNothingClipped()

        tapPin("9999")
        val expected = context.resources.getQuantityString(R.plurals.plurals_pin_tries_left, 4, 4)
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        assertNothingClipped()
    }

    @Test
    fun whileStaffStateIsUnknownTheAppContentIsNotReachable() {
        var clicked = false
        composeRule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                StaffGateLayer(SignInGate.COVER, signOutTick = 0, signIn = { Text("sign-in list") }) {
                    Button(onClick = { clicked = true }) { Text("Sell") }
                }
            }
        }

        // Not in the accessibility tree, and not tappable at its position either.
        assertTrue(composeRule.onAllNodesWithText("Sell").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("sign-in list").fetchSemanticsNodes().isEmpty())
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertFalse(clicked)
    }

    @Test
    fun signingOutDismissesAnOpenPinDialog() {
        var tick by mutableIntStateOf(0)
        composeRule.setContent {
            CompositionLocalProvider(LocalSignOutTick provides tick) {
                var open by remember { mutableStateOf(true) }
                OnSignOut { open = false }
                if (open) Dialog(onDismissRequest = {}) { Text("Enter PIN") }
            }
        }
        composeRule.onNodeWithText("Enter PIN").assertIsDisplayed()

        tick++

        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Enter PIN").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun signingOutDismissesAnOpenAlertDialog() {
        var tick by mutableIntStateOf(0)
        composeRule.setContent {
            CompositionLocalProvider(LocalSignOutTick provides tick) {
                var open by remember { mutableStateOf(true) }
                OnSignOut { open = false }
                if (open) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Restore?") },
                        text = { Text("Replaces everything") },
                        confirmButton = { TextButton(onClick = {}) { Text("Replace") } },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Replace").assertIsDisplayed()

        tick++

        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Replace").fetchSemanticsNodes().isEmpty() }
    }

    /** Every text node: nothing ellipsised, no line wider than its box, no cut height, inside the phone width. */
    private fun assertNothingClipped() {
        val phone = composeRule.onNodeWithTag(PHONE_TAG).fetchSemanticsNode().boundsInRoot
        val nodes = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("no text nodes found", nodes.isNotEmpty())
        nodes.forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "node ${node.id}"
            val results = mutableListOf<TextLayoutResult>()
            val ok = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            assertTrue("'$label' gave no text layout", ok == true && results.isNotEmpty())
            results.forEach {
                assertFalse("'$label' overflows its height", it.didOverflowHeight)
                for (line in 0 until it.lineCount) {
                    assertFalse("'$label' line $line is ellipsised", it.isLineEllipsized(line))
                    assertTrue("'$label' line $line is wider than its box", it.getLineRight(line) - it.getLineLeft(line) <= it.size.width + 0.5f)
                }
            }
            val b = Rect(node.positionInRoot, node.size.toSize())
            assertTrue("'$label' sticks out of the phone width: $b vs $phone", b.left >= phone.left - 0.5f && b.right <= phone.right + 0.5f)
        }
    }

    private companion object {
        const val PHONE_TAG = "phone"
    }
}
