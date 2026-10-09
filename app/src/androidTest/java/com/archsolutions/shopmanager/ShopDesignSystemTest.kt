package com.archsolutions.shopmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.StatTile
import org.junit.Rule
import org.junit.Test

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
}
