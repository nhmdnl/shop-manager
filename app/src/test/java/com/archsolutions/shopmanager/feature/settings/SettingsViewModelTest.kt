package com.archsolutions.shopmanager.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.archsolutions.shopmanager.testutil.FakeClock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme and language updates emit to settings flow`() = testScope.runTest {
        val testFile = tmpFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { testFile },
        )
        val settingsStore = SettingsStore(dataStore)
        val viewModel = SettingsViewModel(settingsStore, FakeClock())

        viewModel.settings.test {
            // Initial default emission
            val initial = awaitItem()
            assertEquals(ThemeMode.LIGHT, initial.theme)
            assertEquals("en", initial.language)

            // Update theme
            viewModel.setTheme(ThemeMode.DARK)
            testScheduler.advanceUntilIdle()
            val afterTheme = awaitItem()
            assertEquals(ThemeMode.DARK, afterTheme.theme)

            // Update language
            viewModel.setLanguage("sw")
            testScheduler.advanceUntilIdle()
            val afterLang = awaitItem()
            assertEquals("sw", afterLang.language)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
