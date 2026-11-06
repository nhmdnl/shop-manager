package com.archsolutions.shopmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.platform.app.InstrumentationRegistry
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.CameraPermissionState
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.feature.scan.ScanScreen
import com.archsolutions.shopmanager.feature.scan.ScanUiState
import com.archsolutions.shopmanager.feature.staff.LocalSignOutTick
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Scan screen against fakes: NO real camera runs here (the VM has none); a physical tablet proves the camera classes. */
class ScanScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeSource(torch: Boolean = true) : ScanSource {
        override val codes = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 4)
        override val hasTorch: StateFlow<Boolean> = MutableStateFlow(torch)
        var torchCalls = mutableListOf<Boolean>()
        override fun setTorch(on: Boolean) { torchCalls += on }

        @Composable
        override fun Preview(modifier: Modifier) {
            Box(modifier.testTag("preview"))
        }
    }

    private class FakeFeedback : ScanFeedback {
        var ok = 0
        var bad = 0
        override fun success() { ok++ }
        override fun failure() { bad++ }
    }

    private class FakePermission(override val state: CameraPermissionState) : CameraPermission {
        var requests = 0
        var settings = 0
        override fun request() { requests++ }
        override fun openSettings() { settings++ }
    }

    private fun text(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(
        source: ScanSource = FakeSource(),
        feedback: ScanFeedback = FakeFeedback(),
        permission: CameraPermission = FakePermission(CameraPermissionState.Granted),
        continuous: Boolean = true,
        state: ScanUiState = ScanUiState(),
        onCode: (ScannedCode) -> Unit = {},
        onAddProduct: (String) -> Unit = {},
        onDone: () -> Unit = {},
    ) = rule.setContent {
        ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
            ScanScreen(source, feedback, permission, continuous, state, onCode, onAddProduct, onDone)
        }
    }

    @Test
    fun showsTheRunningCountAndTheLastItem() {
        show(state = ScanUiState(count = 3, last = "Soap", results = 3))
        rule.onNodeWithText("3 items scanned").assertIsDisplayed()
        rule.onNodeWithText("Last: Soap").assertIsDisplayed()
        rule.onNodeWithTag("preview").assertIsDisplayed()
    }

    @Test
    fun showsNotFoundWithAnAddThisProductButtonForAnUnknownCode() {
        var added: String? = null
        show(state = ScanUiState(notFound = "9999", results = 1), onAddProduct = { added = it })
        rule.onNodeWithText("Not found: 9999").assertIsDisplayed()
        rule.onNodeWithText("Add this product").assertIsDisplayed().performClick()
        assertEquals("9999", added)
    }

    @Test
    fun permissionDeniedShowsTheMessageAndOpenSettingsAndNoPreview() {
        val permission = FakePermission(CameraPermissionState.Denied)
        show(permission = permission)
        rule.onNodeWithText(text(R.string.scan_permission_needed)).assertIsDisplayed()
        rule.onNodeWithText("Open settings").assertIsDisplayed().performClick()
        rule.onNodeWithTag("preview").assertDoesNotExist()
        rule.onNodeWithText("Allow camera").assertDoesNotExist()
        assertEquals(1, permission.settings)
    }

    @Test
    fun notAskedShowsAllowCameraWhichRequestsThePermission() {
        val permission = FakePermission(CameraPermissionState.NotAsked)
        show(permission = permission)
        rule.onNodeWithTag("preview").assertDoesNotExist()
        rule.onNodeWithText("Allow camera").assertIsDisplayed().performClick()
        assertEquals(1, permission.requests)
    }

    @Test
    fun doneCallsOnDone() {
        var done = 0
        show(onDone = { done++ })
        rule.onNodeWithText("Done").assertIsDisplayed().performClick()
        assertEquals(1, done)
    }

    @Test
    fun singleShotShowsCancelInsteadOfDone() {
        var done = 0
        show(continuous = false, onDone = { done++ })
        rule.onNodeWithText("Done").assertDoesNotExist()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, done)
    }

    @Test
    fun torchButtonIsHiddenWhenTheSourceHasNoTorch() {
        show(source = FakeSource(torch = false))
        rule.onNodeWithText("Torch").assertDoesNotExist()
    }

    @Test
    fun torchButtonTogglesTheTorchWhenThereIsOne() {
        val source = FakeSource(torch = true)
        show(source = source)
        rule.onNodeWithText("Torch").performClick()
        rule.onNodeWithText("Torch").performClick()
        assertEquals(listOf(true, false), source.torchCalls)
    }

    @Test
    fun codesFromTheSourceReachOnCodeOnlyWhileThePermissionIsGranted() {
        val got = mutableListOf<String>()
        val source = FakeSource()
        show(source = source, onCode = { got += it.text })
        rule.waitForIdle()
        source.codes.tryEmit(ScannedCode("6001"))
        rule.waitUntil(2_000) { got.isNotEmpty() }
        assertEquals(listOf("6001"), got)
    }

    @Test
    fun codesAreIgnoredWhileThePermissionIsDenied() {
        val got = mutableListOf<String>()
        val source = FakeSource()
        show(source = source, permission = FakePermission(CameraPermissionState.Denied), onCode = { got += it.text })
        rule.waitForIdle()
        source.codes.tryEmit(ScannedCode("6001"))
        Thread.sleep(300)
        rule.waitForIdle()
        assertTrue(got.isEmpty())
    }

    @Test
    fun feedbackFollowsStateChanges() {
        val feedback = FakeFeedback()
        val results = mutableIntStateOf(1)
        val notFound = mutableStateOf<String?>(null)
        rule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                ScanScreen(
                    FakeSource(), feedback, FakePermission(CameraPermissionState.Granted), true,
                    ScanUiState(count = 1, last = "Soap", notFound = notFound.value, results = results.intValue),
                    {}, {}, {},
                )
            }
        }
        rule.waitForIdle()
        assertEquals(0, feedback.ok + feedback.bad)
        rule.runOnIdle { results.intValue = 2 }
        rule.waitUntil(2_000) { feedback.ok == 1 }
        rule.runOnIdle { notFound.value = "9999"; results.intValue = 3 }
        rule.waitUntil(2_000) { feedback.bad == 1 }
        assertEquals(1, feedback.ok)
    }

    @Test
    fun closesOnSignOut() {
        var done = 0
        val tick = mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalSignOutTick provides tick.intValue) {
                ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                    ScanScreen(
                        FakeSource(), FakeFeedback(), FakePermission(CameraPermissionState.Granted), true,
                        ScanUiState(), {}, {}, { done++ },
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, done)
        rule.runOnIdle { tick.intValue = 1 }
        rule.waitUntil(2_000) { done == 1 }
    }

    // --- 200% font on a 390 dp wide phone box: nothing ellipsised, cut or sticking out ---

    private fun phoneAt200(state: ScanUiState, permission: CameraPermission = FakePermission(CameraPermissionState.Granted)) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredWidth(390.dp).fillMaxHeight().testTag("phone")) {
                            ScanScreen(FakeSource(), FakeFeedback(), permission, true, state, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    private fun assertNothingClipped() {
        val phone = rule.onNodeWithTag("phone").fetchSemanticsNode().boundsInRoot
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
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
                    assertTrue(
                        "'$label' line $line is wider than its box",
                        it.getLineRight(line) - it.getLineLeft(line) <= it.size.width + 0.5f,
                    )
                }
            }
            val b = Rect(node.positionInRoot, node.size.toSize())
            assertTrue("'$label' sticks out of the phone width: $b vs $phone", b.left >= phone.left - 0.5f && b.right <= phone.right + 0.5f)
            assertTrue("'$label' sticks out of the phone height: $b vs $phone", b.top >= phone.top - 0.5f && b.bottom <= phone.bottom + 0.5f)
        }
    }

    @Test
    fun doesNotClipAt200PercentFontWhileScanning() {
        phoneAt200(ScanUiState(count = 12, last = "Mukwano Soap Bar 800g Family Pack", notFound = "6001234567890", results = 13))
        rule.onNodeWithText("Done").assertIsDisplayed()
        rule.onNodeWithText("Add this product").assertIsDisplayed()
        assertNothingClipped()
    }

    @Test
    fun doesNotClipAt200PercentFontWithoutPermission() {
        phoneAt200(ScanUiState(), FakePermission(CameraPermissionState.NotAsked))
        rule.onNodeWithText("Allow camera").assertIsDisplayed()
        rule.onNodeWithText("Open settings").assertIsDisplayed()
        assertNothingClipped()
    }
}
