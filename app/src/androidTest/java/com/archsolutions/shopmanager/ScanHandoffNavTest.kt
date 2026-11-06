package com.archsolutions.shopmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.clickable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.app.ScanOnceDestination
import com.archsolutions.shopmanager.app.ScannedCodeHandoff
import com.archsolutions.shopmanager.app.popIfResumed
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.CameraPermissionState
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.ScannedCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The single-shot scanner's handoff through a REAL NavHost (fake camera): the code the scanner route leaves must
 * reach the screen below it (Edit product / Stock use ScannedCodeHandoff the same way), exactly once. The first
 * version of this handoff read a ViewModel's own SavedStateHandle and delivered nothing.
 */
@RunWith(AndroidJUnit4::class)
class ScanHandoffNavTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeSource : ScanSource {
        override val unavailable: StateFlow<Boolean> = MutableStateFlow(false)
        override fun retry() {}
        override val codes = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 4)
        override val hasTorch: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setTorch(on: Boolean) {}

        @Composable
        override fun Preview(modifier: Modifier) {
            Box(modifier.testTag("preview"))
        }
    }

    private object QuietFeedback : ScanFeedback {
        override fun success() {}
        override fun failure() {}
    }

    private object Granted : CameraPermission {
        override val state = CameraPermissionState.Granted
        override fun request() {}
        override fun openSettings() {}
    }

    private val received = mutableListOf<String>()
    private val source = FakeSource()
    private lateinit var nav: NavHostController

    private fun show() {
        rule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                nav = rememberNavController()
                NavHost(nav, startDestination = "host") {
                    composable("host") { entry ->
                        ScannedCodeHandoff(entry) { received += it }
                        Text("host screen", Modifier.clickable { nav.navigate("scan") })
                    }
                    composable("scan") { entry ->
                        ScanOnceDestination(nav, entry, source, QuietFeedback, Granted)
                    }
                }
            }
        }
    }

    @Test
    fun scannedCodeReachesTheScreenBelowOnceAndTheScannerCloses() {
        show()
        rule.onNodeWithText("host screen").performClick()
        rule.onNodeWithTag("preview").assertIsDisplayed()
        rule.runOnIdle { source.codes.tryEmit(ScannedCode(" 6001 ")); source.codes.tryEmit(ScannedCode("6002")) }
        rule.waitUntil(5_000) { received.isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTagCount("preview") == 0 }
        rule.onNodeWithText("host screen").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf("6001"), received) } // trimmed, one-shot even with a second frame
        rule.runOnIdle { assertEquals("host", nav.currentBackStackEntry?.destination?.route) }
    }

    @Test
    fun cancelLeavesWithoutDeliveringACode() {
        show()
        rule.onNodeWithText("host screen").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.scan_cancel)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTagCount("preview") == 0 }
        rule.runOnIdle { assertEquals(emptyList<String>(), received) }
    }

    @Test
    fun aSecondPopWhileTheScannerIsLeavingDoesNotPopTheScreenBelow() {
        rule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                nav = rememberNavController()
                NavHost(nav, startDestination = "a") {
                    composable("a") { Text("a") }
                    composable("b") { Text("b") }
                    composable("c") { Text("c") }
                }
            }
        }
        rule.runOnIdle { nav.navigate("b") }
        rule.waitForIdle()
        rule.runOnIdle { nav.navigate("c") }
        rule.waitForIdle()
        rule.runOnIdle {
            val c = nav.currentBackStackEntry!!
            nav.popIfResumed(c)
            nav.popIfResumed(c) // the double tap
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("b", nav.currentBackStackEntry?.destination?.route) }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
