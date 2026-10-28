package com.archsolutions.shopmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.archsolutions.cloud.contract.Period
import com.archsolutions.cloud.contract.PlanDto
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.StatTile
import com.archsolutions.shopmanager.feature.cloud.CloudCardState
import com.archsolutions.shopmanager.feature.cloud.CloudPayContent
import com.archsolutions.shopmanager.feature.cloud.CloudSettingsCardContent
import com.archsolutions.shopmanager.feature.cloud.CloudSignInContent
import com.archsolutions.shopmanager.feature.cloud.PayActions
import com.archsolutions.shopmanager.feature.cloud.PayPhase
import com.archsolutions.shopmanager.feature.cloud.PayUiState
import com.archsolutions.shopmanager.feature.cloud.SignInActions
import com.archsolutions.shopmanager.feature.cloud.SignInError
import com.archsolutions.shopmanager.feature.cloud.SignInStep
import com.archsolutions.shopmanager.feature.cloud.SignInUiState
import org.junit.Rule
import org.junit.Test

private const val PHONE_TAG = "phone"

class ShopDesignSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPrimaryButtonAndStatTileRender() {
        composeTestRule.setContent {
            ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                PrimaryButton(
                    text = "New sale",
                    onClick = {},
                )
                StatTile(
                    label = "Total sales",
                    amountFormatted = "UGX 195,900",
                )
            }
        }

        composeTestRule.onNodeWithText("New sale").assertIsDisplayed()
        composeTestRule.onNodeWithText("UGX 195,900").assertIsDisplayed()
    }

    // --- Arch-SME Cloud screens at 200% fonts on a 390 x 844 dp phone ---
    // assertIsDisplayed fails when a node is laid out outside the phone box or cut off by its parent.

    private fun phoneAt200(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                ShopManagerTheme(themeMode = ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredSize(390.dp, 844.dp).testTag(PHONE_TAG)) { content() }
                    }
                }
            }
        }
    }

    /**
     * assertIsDisplayed passes for half-visible nodes and cannot see ellipsised or cut text, so every text
     * node is also checked here: no overflow in its text layout, and its unclipped bounds inside the phone box width.
     */
    private fun assertNothingClipped() {
        val phone = composeTestRule.onNodeWithTag(PHONE_TAG).fetchSemanticsNode().boundsInRoot
        val nodes = composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("no text nodes found", nodes.isNotEmpty())
        nodes.forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "node ${node.id}"
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach {
                assertFalse("'$label' has visual overflow", it.hasVisualOverflow)
                assertFalse("'$label' overflows its height", it.didOverflowHeight)
                assertFalse("'$label' overflows its width", it.didOverflowWidth)
            }
            val b = Rect(node.positionInRoot, node.size.toSize())
            assertTrue("'$label' sticks out of the phone width: $b vs $phone", b.left >= phone.left - 0.5f && b.right <= phone.right + 0.5f)
        }
    }

    private fun text(resId: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun shown(resId: Int) {
        composeTestRule.onNodeWithText(text(resId)).assertIsDisplayed()
        assertNothingClipped()
    }

    /** The screens scroll, so a node below the fold is scrolled to first; it must then fit the phone box. */
    private fun shownScrolled(resId: Int) {
        composeTestRule.onNodeWithText(text(resId)).performScrollTo().assertIsDisplayed()
        assertNothingClipped()
    }

    @Test
    fun cloudCardSignedOutAt200() {
        phoneAt200 { CloudSettingsCardContent(CloudCardState(visible = true, backupLine = "Not linked to Arch-SME Cloud"), {}, {}, {}, {}, {}) }
        shown(R.string.cloud_card_title)
        shown(R.string.cloud_sign_in)
    }

    @Test
    fun cloudCardSignedInAt200() {
        phoneAt200 {
            CloudSettingsCardContent(
                CloudCardState(
                    visible = true,
                    signedIn = true,
                    planLine = "Monthly plan until 24 November 2026",
                    backupLine = "Last cloud backup: today 18:30",
                    canBackUpNow = true,
                ),
                {}, {}, {}, {}, {},
            )
        }
        shown(R.string.cloud_card_title)
        shown(R.string.cloud_back_up_now)
        shown(R.string.cloud_pay)
        shown(R.string.cloud_restore)
        shown(R.string.cloud_unlink)
    }

    private fun signIn(state: SignInUiState) = phoneAt200 { CloudSignInContent(state, SignInActions(), onDone = {}) }

    @Test
    fun cloudSignInPhoneStepAt200() {
        signIn(SignInUiState(step = SignInStep.PHONE, phone = "0772123456"))
        shown(R.string.cloud_signin_title)
        shownScrolled(R.string.cloud_phone_title)
        shownScrolled(R.string.cloud_send_code)
    }

    @Test
    fun cloudSignInCodeStepAt200() {
        signIn(SignInUiState(step = SignInStep.CODE, phone = "0772123456", code = "123456", canResend = true))
        shownScrolled(R.string.cloud_code_title)
        shownScrolled(R.string.cloud_confirm_code)
        shownScrolled(R.string.cloud_resend)
    }

    @Test
    fun cloudSignInProfileStepAt200() {
        signIn(SignInUiState(step = SignInStep.PROFILE, name = "Nakato", consent = true))
        shownScrolled(R.string.cloud_profile_title)
        shownScrolled(R.string.cloud_consent)
        shownScrolled(R.string.cloud_privacy_link)
        shownScrolled(R.string.cloud_terms_link)
        shownScrolled(R.string.cloud_link_tablet)
    }

    @Test
    fun cloudSignInReplaceStepAt200() {
        signIn(SignInUiState(step = SignInStep.REPLACE))
        shownScrolled(R.string.cloud_replace_title)
        shownScrolled(R.string.cloud_replace_yes)
        shownScrolled(R.string.cloud_replace_no)
    }

    @Test
    fun cloudSignInErrorAt200() {
        signIn(SignInUiState(step = SignInStep.PHONE, phone = "07", error = SignInError.PHONE))
        shownScrolled(R.string.cloud_error_phone)
    }

    private val plans = listOf(
        PlanDto("m", "Monthly", 5_000, Period.MONTH),
        PlanDto("w", "Weekly", 6_000, Period.WEEK),
    )

    private fun pay(state: PayUiState) = phoneAt200 { CloudPayContent(state, PayActions(), onDone = {}) }

    @Test
    fun cloudPayChooseAt200() {
        pay(PayUiState(plans = plans, selected = "m", msisdn = "0772123456", online = true, loading = false))
        shown(R.string.cloud_pay_title)
        shownScrolled(R.string.cloud_pay_number)
        shownScrolled(R.string.cloud_pay_button)
    }

    @Test
    fun cloudPayWaitingAndPaidAt200() {
        pay(PayUiState(plans = plans, loading = false, phase = PayPhase.PAID))
        shownScrolled(R.string.cloud_pay_paid)
        shownScrolled(R.string.cloud_pay_done)
    }

    @Test
    fun cloudPayFailedAt200() {
        pay(PayUiState(plans = plans, loading = false, phase = PayPhase.FAILED))
        shownScrolled(R.string.cloud_pay_failed)
        shownScrolled(R.string.cloud_pay_retry)
    }
}
