package com.archsolutions.shopmanager.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.archsolutions.shopmanager.feature.scan.ScanHost
import com.archsolutions.shopmanager.feature.scan.ScanOnceHost
import com.archsolutions.shopmanager.feature.stock.SCANNED_BARCODE_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import androidx.navigation.compose.rememberNavController
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.feature.day.CloseDayScreen
import com.archsolutions.shopmanager.feature.day.DayClosedScreen
import com.archsolutions.shopmanager.feature.day.DayScreen
import com.archsolutions.shopmanager.feature.day.YesterdayOpenScreen
import com.archsolutions.shopmanager.feature.day.DebtorsScreen
import com.archsolutions.shopmanager.feature.day.RecordMoneyScreen
import com.archsolutions.shopmanager.feature.day.OpenDayScreen
import com.archsolutions.shopmanager.feature.home.HomeScreen
import com.archsolutions.shopmanager.feature.home.HomeViewModel
import com.archsolutions.shopmanager.feature.sales.SaleDetailScreen
import com.archsolutions.shopmanager.feature.sales.SalesScreen
import com.archsolutions.shopmanager.feature.sell.CheckoutScreen
import com.archsolutions.shopmanager.feature.sell.SellViewModel
import com.archsolutions.shopmanager.feature.reports.ReportsScreen
import com.archsolutions.shopmanager.feature.sell.SellScreen
import com.archsolutions.shopmanager.feature.cloud.CloudPayScreen
import com.archsolutions.shopmanager.feature.cloud.CloudRestoreScreen
import com.archsolutions.shopmanager.feature.cloud.CloudSignInScreen
import com.archsolutions.shopmanager.feature.cloud.CloudViewModel
import com.archsolutions.shopmanager.feature.settings.SettingsScreen
import com.archsolutions.shopmanager.feature.settings.SettingsViewModel
import com.archsolutions.shopmanager.feature.stock.AddPurchaseScreen
import com.archsolutions.shopmanager.feature.stock.EditProductScreen
import com.archsolutions.shopmanager.feature.stock.PurchaseViewModel
import com.archsolutions.shopmanager.feature.stock.StockScreen
import com.archsolutions.shopmanager.feature.suppliers.SupplierDetailScreen
import com.archsolutions.shopmanager.feature.suppliers.SupplierEditScreen
import com.archsolutions.shopmanager.feature.suppliers.SuppliersScreen
import kotlinx.serialization.Serializable
import com.archsolutions.shopmanager.feature.pin.rememberPinGate
import com.archsolutions.shopmanager.feature.staff.LocalSignOutTick
import com.archsolutions.shopmanager.feature.staff.StaffGate
import com.archsolutions.shopmanager.feature.staff.StaffGateViewModel
import com.archsolutions.shopmanager.feature.staff.StaffScreen
import com.archsolutions.shopmanager.feature.backup.BackupReminderViewModel
import com.archsolutions.shopmanager.feature.backup.rememberBackupActions
import com.archsolutions.shopmanager.data.backup.AutoBackupWorker
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import com.archsolutions.shopmanager.feature.onboarding.OnboardingScreen
import com.archsolutions.shopmanager.feature.day.PastDayScreen

// Type-safe route objects
@Serializable object HomeRoute
@Serializable object SellRoute
@Serializable data class StockRoute(val supplierId: Long = 0L) // tablet "Record purchase" preselects the supplier
@Serializable object DayRoute
@Serializable object SuppliersRoute
@Serializable object ReportsRoute
@Serializable object SettingsRoute
@Serializable object OpenDayRoute
@Serializable object SellGraph // Sell + phone Checkout share one SellViewModel (the cart)
@Serializable object CheckoutRoute
@Serializable data class SalesRoute(val dayId: Long = 0L) // 0 = the day the gate is on
@Serializable data class SaleDetailRoute(val id: Long)
@Serializable data class AddPurchaseRoute(val productId: Long = 0L, val supplierId: Long = 0L) // 0 = none
@Serializable data class EditProductRoute(val id: Long = 0L, val barcode: String = "") // 0 = new product; barcode only prefills a new one
@Serializable data class RecordMoneyRoute(val type: String = "EXPENSE", val customerId: Long = 0L) // MoneyEntryType name
@Serializable object DebtorsRoute
@Serializable data class CloseDayRoute(val date: String)
@Serializable data class DayClosedRoute(val dayId: Long, val sent: Boolean = false)
@Serializable data class YesterdayOpenRoute(val date: String)
@Serializable object OnboardingRoute
@Serializable data class DaySummaryRoute(val dayId: Long) // a closed day's record, read-only (Reports > Days)
@Serializable data class SupplierDetailRoute(val id: Long)
@Serializable data class SupplierEditRoute(val id: Long = 0L) // 0 = new supplier
@Serializable object ScanSellRoute // continuous scanner inside SellGraph (wired in the Sell host)
@Serializable object ScanPurchaseRoute // continuous scanner for Record purchase
@Serializable object ScanOnceRoute // single-shot scanner (Edit product barcode, Stock lookup)
@Serializable object CloudPayRoute
@Serializable object CloudRestoreRoute
@Serializable object StaffRoute
@Serializable data class CloudSignInRoute(val purpose: String = "LINK") // LINK goes back when linked; RESTORE goes on to CloudRestoreRoute

enum class NavDestination(
    val route: Any,
    val labelResId: Int,
    val icon: ImageVector,
    val showInCompactBottomBar: Boolean = true,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Outlined.Home),
    // The graph, not SellRoute: a tab switch saves the stack under the graph, so only it restores the cart.
    SELL(SellGraph, R.string.nav_sell, Icons.Outlined.ShoppingCart),
    STOCK(StockRoute(), R.string.nav_stock, Icons.Outlined.Inventory2),
    DAY(DayRoute, R.string.nav_day, Icons.Outlined.AccountBalanceWallet, showInCompactBottomBar = false),
    SUPPLIERS(SuppliersRoute, R.string.nav_suppliers, Icons.Outlined.LocalShipping),
    REPORTS(ReportsRoute, R.string.nav_reports, Icons.Outlined.BarChart),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings, showInCompactBottomBar = false),
}

/** The app shell, with the staff sign-in gate laid over it while staff is on and nobody is signed in. */
@Composable
fun AppNavHost(
    isExpanded: Boolean,
    // 3.5: medium widths (portrait tablets) keep the rail; screens go two-pane only when expanded.
    useRail: Boolean = isExpanded,
    openCloseDay: Boolean = false,
    onCloseDayOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    gateViewModel: DayGateViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val needsOnboarding by gateViewModel.needsOnboarding.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize()) {
        // Unknown onboarding stays null: the gate fails closed until it and the staff setting are read.
        StaffGate(onboarding = needsOnboarding) {
            AppShell(isExpanded, useRail, openCloseDay, onCloseDayOpened, navController, settingsViewModel, gateViewModel)
        }
    }
}

/** Screens reached only through an Owner-gated tap (restore, cloud sign-in/pay, staff). Nothing else may keep them open. */
internal val entryGatedRoutes = listOf(CloudRestoreRoute::class, CloudSignInRoute::class, CloudPayRoute::class, StaffRoute::class)

/** True when any route on [routes] is an entry-gated one: such a stack is never saved by tab switching (N1). */
internal fun stackHoldsGatedRoute(routes: List<kotlin.reflect.KClass<*>>): Boolean = routes.any { it in entryGatedRoutes }

/**
 * N2: the owner-only screens are popped only when staff is on and nobody is signed in. The session is in memory, so
 * after process death nobody is signed in and the pop still happens; a rotation with an Owner signed in keeps them.
 * Unknown (null) never pops here: the gate itself covers unknown.
 */
internal fun shouldDropGatedRoutes(staffActive: Boolean?, signedInId: Long?): Boolean = staffActive == true && signedInId == null

private fun NavHostController.currentStackHoldsGated(): Boolean = currentBackStack.value.any { entry ->
    entryGatedRoutes.any { entry.destination.hasRoute(it) }
}

/** Pops every entry-gated screen off the stack (no-op for those not on it). */
internal fun NavHostController.dropEntryGated() {
    popBackStack<CloudRestoreRoute>(true)
    popBackStack<CloudSignInRoute>(true)
    popBackStack<CloudPayRoute>(true)
    popBackStack<StaffRoute>(true)
}

@Composable
private fun AppShell(
    isExpanded: Boolean,
    // 3.5: medium widths (portrait tablets) keep the rail; screens go two-pane only when expanded.
    useRail: Boolean = isExpanded,
    openCloseDay: Boolean = false,
    onCloseDayOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    gateViewModel: DayGateViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val gateState by gateViewModel.state.collectAsStateWithLifecycle()

    // 6.3: re-run the gate whenever the app comes back, so a new day after midnight is noticed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { gateViewModel.onForeground() }

    val needsOnboarding by gateViewModel.needsOnboarding.collectAsStateWithLifecycle()
    val shopSettings by settingsViewModel.settings.collectAsStateWithLifecycle()

    if (gateState == DayGateState.Loading || needsOnboarding == null) {
        Box(modifier = modifier.fillMaxSize().background(colors.paper))
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Gate steps are full-screen on every layout (no rail on the design boards).
    val isOpenDay = currentRoute == OpenDayRoute::class.qualifiedName ||
        currentDestination?.hasRoute(YesterdayOpenRoute::class) == true ||
        currentDestination?.hasRoute(OnboardingRoute::class) == true
    // Hold gate prompts while a day is being opened or closed: Open the day pushed over Close the day
    // was popped along with it, so closing yesterday never led on to opening today.
    val canPrompt = currentDestination != null && !isOpenDay && needsOnboarding == false &&
        !currentDestination.hasRoute(CloseDayRoute::class) && !currentDestination.hasRoute(DayClosedRoute::class)

    // C1: these screens were opened by an Owner-gated tap. After a sign-out, or a restored back stack once staff is
    // on, the next person must not land on them (a Cashier on the restore list could confirm a restore with no PIN).
    val staffGateViewModel: StaffGateViewModel = hiltViewModel()
    val staffActive by staffGateViewModel.staffActive.collectAsStateWithLifecycle()
    val signedIn by staffGateViewModel.current.collectAsStateWithLifecycle()
    val signOutTick = LocalSignOutTick.current
    val firstTick = remember { signOutTick }
    // Also keyed on the current entry: a tab restore with nobody signed in drops any owner-only screen that came back (N1).
    LaunchedEffect(staffActive, signedIn?.id, currentDestination != null, navBackStackEntry?.id) {
        if (shouldDropGatedRoutes(staffActive, signedIn?.id) && currentDestination != null) navController.dropEntryGated()
    }
    LaunchedEffect(signOutTick) {
        if (signOutTick != firstTick) navController.dropEntryGated()
    }

    // 7.8: a fresh install starts with onboarding, whose last step opens the first day.
    LaunchedEffect(needsOnboarding, currentDestination != null) {
        // Sign-in and restore are reached from onboarding: after a rotation or process restore the stack is
        // [Onboarding, CloudSignIn], so a second Onboarding must not be pushed over them.
        if (needsOnboarding == true && currentDestination != null &&
            !currentDestination.hasRoute(OnboardingRoute::class) &&
            !currentDestination.hasRoute(CloudSignInRoute::class) &&
            !currentDestination.hasRoute(CloudRestoreRoute::class)
        ) {
            navController.navigate(OnboardingRoute) { popUpTo<HomeRoute> { inclusive = true } }
        }
    }

    LaunchedEffect(gateState, canPrompt) {
        if (!canPrompt) return@LaunchedEffect
        when (val prompt = gateViewModel.consumePrompt()) {
            GatePrompt.OpenDay -> navController.navigate(OpenDayRoute) { launchSingleTop = true }
            is GatePrompt.YesterdayOpen -> navController.navigate(YesterdayOpenRoute(prompt.date)) { launchSingleTop = true }
            null -> Unit
        }
    }

    // The close-day reminder (6.7) goes to Close the day while today is open; otherwise Home shows why not.
    LaunchedEffect(openCloseDay, currentDestination != null) {
        if (!openCloseDay || currentDestination == null) return@LaunchedEffect
        onCloseDayOpened()
        (gateState as? DayGateState.Ready)?.let { navController.navigate(CloseDayRoute(it.day.date)) { launchSingleTop = true } }
    }

    // Check which top-level item is selected
    // hasRoute, not route strings: routes with arguments (StockRoute) carry them in the string.
    // The hierarchy covers Sell, whose tab route is its graph.
    fun isSelected(dest: NavDestination): Boolean {
        val destination = currentDestination ?: return false
        return destination.hierarchy.any { it.hasRoute(dest.route::class) } ||
            (dest == NavDestination.SUPPLIERS && destination.hasRoute(SupplierDetailRoute::class))
    }

    if (useRail) {
        // Expanded layout (Tablet): Navigation Rail (104 dp wide) + content
        Row(modifier = modifier.fillMaxSize().background(colors.paper)) {
            // Open the day is a full-screen task on tablet too (no rail on the design board), and so is
            // onboarding, including the cloud sign-in and restore it leads to.
            if (!isOpenDay && needsOnboarding != true) {
                // NavigationRail
                NavigationRail(
                    modifier = Modifier
                        .width(104.dp)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, colors.line)),
                    containerColor = colors.surface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                        ) {
                            // 48 dp green rounded square with shop initial
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.green),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    // The shop initial (it was a fixed "M").
                                    text = shopSettings.shopName.trim().take(1).uppercase().ifEmpty { "S" },
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = colors.onGreen,
                                )
                            }
                        }
                    },
                ) {
                    // Rail Destinations (Home, Sell, Stock, Day, Suppliers, Reports)
                    val railItems = listOf(
                        NavDestination.HOME,
                        NavDestination.SELL,
                        NavDestination.STOCK,
                        NavDestination.DAY,
                        NavDestination.SUPPLIERS,
                        NavDestination.REPORTS,
                    )
    
                    railItems.forEach { dest ->
                        val selected = isSelected(dest)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(dest) },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = stringResource(dest.labelResId),
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(dest.labelResId),
                                    style = MaterialTheme.typography.labelSmall,
                                    // One line at any font size or language: shrinks only when the label would wrap ("Supplier s").
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            modifier = Modifier
                                .height(72.dp)
                                .padding(vertical = 2.dp),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.green,
                                selectedTextColor = colors.green,
                                indicatorColor = colors.greenTint,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                            ),
                        )
                    }
    
                    Spacer(modifier = Modifier.weight(1f))
    
                    // Settings pinned at bottom
                    val settingsSelected = isSelected(NavDestination.SETTINGS)
                    NavigationRailItem(
                        selected = settingsSelected,
                        onClick = { navController.navigateToTab(NavDestination.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.nav_settings),
                                style = MaterialTheme.typography.labelSmall,
                                // One line at any font size or language: shrinks only when the label would wrap ("Supplier s").
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
                                fontWeight = if (settingsSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        modifier = Modifier
                            .height(72.dp)
                            .padding(bottom = 12.dp),
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = colors.green,
                            selectedTextColor = colors.green,
                            indicatorColor = colors.greenTint,
                            unselectedIconColor = colors.muted,
                            unselectedTextColor = colors.muted,
                        ),
                    )
                }
            }

            // Main Content Area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavContent(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    gateViewModel = gateViewModel,
                    gateState = gateState,
                    isExpanded = isExpanded,
                    onTab = navController::navigateToTab,
                )
            }
        }
    } else {
        // Compact layout (Phone): Scaffold with Bottom NavigationBar (76 dp)
        val bottomNavDestinations = listOf(
            NavDestination.HOME,
            NavDestination.SELL,
            NavDestination.STOCK,
            NavDestination.SUPPLIERS,
            NavDestination.REPORTS,
        )

        val destination = navBackStackEntry?.destination
        val isFullscreenTask = isOpenDay ||
            currentRoute == SettingsRoute::class.qualifiedName ||
            currentRoute == CheckoutRoute::class.qualifiedName ||
            destination?.hasRoute(AddPurchaseRoute::class) == true ||
            destination?.hasRoute(EditProductRoute::class) == true ||
            destination?.hasRoute(RecordMoneyRoute::class) == true ||
            destination?.hasRoute(CloseDayRoute::class) == true ||
            destination?.hasRoute(DayClosedRoute::class) == true ||
            destination?.hasRoute(DaySummaryRoute::class) == true ||
            destination?.hasRoute(SupplierEditRoute::class) == true ||
            destination?.hasRoute(CloudSignInRoute::class) == true ||
            destination?.hasRoute(CloudPayRoute::class) == true ||
            destination?.hasRoute(CloudRestoreRoute::class) == true ||
            destination?.hasRoute(StaffRoute::class) == true

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.paper,
            bottomBar = {
                if (!isFullscreenTask) {
                    // 76 dp of items above the system navigation inset, which NavigationBar pads inside itself.
                    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val labelSize = rememberNavBarLabelSize(bottomNavDestinations.map { stringResource(it.labelResId) })
                    NavigationBar(
                        modifier = Modifier.height(76.dp + systemNavInset),
                        containerColor = colors.surface,
                    ) {
                        bottomNavDestinations.forEach { dest ->
                            val selected = isSelected(dest)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(dest) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelResId),
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                label = {
                                    // One line: a wrapped label pushes the icon out of the fixed-height bar at large fonts.
                                    Text(
                                        text = stringResource(dest.labelResId),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = labelSize,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.green,
                                    selectedTextColor = colors.green,
                                    indicatorColor = colors.greenTint,
                                    unselectedIconColor = colors.muted,
                                    unselectedTextColor = colors.muted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                NavContent(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    gateViewModel = gateViewModel,
                    gateState = gateState,
                    isExpanded = false,
                    onTab = navController::navigateToTab,
                )
            }
        }
    }
}

/**
 * One font size for every bottom-bar label: the theme's size when the widest label fits its item, else the
 * largest that does ("Sup…" at 200%), never below the 100% size. Longer labels then end in an ellipsis.
 */
@Composable
private fun rememberNavBarLabelSize(labels: List<String>): TextUnit {
    // Bold, the selected weight, is the widest.
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val measurer = rememberTextMeasurer()
    return remember(labels, style, density, windowWidth) {
        // NavigationBar spaces its items 8 dp apart; 4 dp more keeps neighbouring labels from touching.
        val itemWidth = with(density) { (windowWidth - 8.dp.roundToPx() * (labels.size - 1)) / labels.size - 4.dp.roundToPx() }
        val full = style.fontSize.value
        val floor = full / density.fontScale.coerceAtLeast(1f)
        val fits = { size: Float ->
            labels.all { label ->
                measurer.measure(label, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width <= itemWidth
            }
        }
        (generateSequence(full) { it - 0.5f }.takeWhile { it > floor }.firstOrNull(fits) ?: floor).sp
    }
}

@Composable
private fun NavContent(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    gateViewModel: DayGateViewModel,
    gateState: DayGateState,
    isExpanded: Boolean,
    // Home's tiles open tabs the way the rail does, or a stack saved for that tab comes back stale later.
    onTab: (NavDestination) -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val today by gateViewModel.today.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openDay = { navController.navigate(OpenDayRoute) { launchSingleTop = true } }
    // Closes the day the gate is on: today's, or the earlier day still waiting to be closed.
    val closeDay = {
        gateState.currentDay?.date?.let { date -> navController.navigate(CloseDayRoute(date)) { launchSingleTop = true } }
        Unit
    }
    // 7.5: tablets open Stock with the Add purchase panel on this supplier; phones the full-screen form.
    val recordPurchase = { supplierId: Long ->
        if (isExpanded) navController.openStockForSupplier(supplierId) else navController.navigate(AddPurchaseRoute(supplierId = supplierId))
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val recentSales by homeViewModel.recentSales.collectAsStateWithLifecycle()
            val lowStock by homeViewModel.lowStock.collectAsStateWithLifecycle()
            val todaySummary by homeViewModel.todaySummary.collectAsStateWithLifecycle()
            val pinGate = rememberPinGate()
            val backupReminderViewModel: BackupReminderViewModel = hiltViewModel()
            val backupReminder by backupReminderViewModel.reminder.collectAsStateWithLifecycle()
            val homeSnackbar = remember { SnackbarHostState() }
            val staffGate: StaffGateViewModel = hiltViewModel()
            val signedIn by staffGate.current.collectAsStateWithLifecycle()
            val staffActive by staffGate.staffActive.collectAsStateWithLifecycle()
            val cloudViewModel: CloudViewModel = hiltViewModel()
            val cloudNotice by cloudViewModel.notice.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { cloudViewModel.refresh() }
            val backup = rememberBackupActions(homeSnackbar)
            HomeScreen(
                isExpanded = isExpanded,
                shopName = settings.shopName,
                today = today,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onNavigateToSell = { onTab(NavDestination.SELL) },
                onNavigateToStock = { onTab(NavDestination.STOCK) },
                onAddExpense = {
                    if (isExpanded) onTab(NavDestination.DAY) else navController.navigate(RecordMoneyRoute())
                },
                onCloseDay = closeDay,
                onDayDetails = { onTab(NavDestination.DAY) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onOpenDay = openDay,
                recentSales = recentSales,
                lowStock = lowStock,
                onSeeAllSales = { navController.navigate(SalesRoute()) },
                onOpenSale = { navController.navigate(SaleDetailRoute(it)) },
                todaySummary = todaySummary,
                signedInName = signedIn?.name,
                onSwitchUser = staffGate::switchUser,
                onDaySummary = { navController.navigate(DayClosedRoute(it)) },
                onReopenDay = { dayId -> pinGate.run(R.string.pin_for_reopen) { homeViewModel.reopenDay(dayId) } },
                backupReminder = backupReminder,
                // A backup zip carries every PIN hash: with staff on (or not yet known) it needs an Owner, like Settings does.
                onBackUp = {
                    if (staffActive == false) backup.backUp() else pinGate.run(R.string.pin_for_backup, backup.backUp)
                },
                cloudNotice = cloudNotice,
                onCloudNotice = { pinGate.run(R.string.pin_for_cloud) { navController.navigate(CloudPayRoute) } },
                snackbarHostState = homeSnackbar,
            )
        }
        navigation<SellGraph>(startDestination = SellRoute) {
            composable<SellRoute> { entry ->
                SellScreen(
                    isExpanded = isExpanded,
                    gateState = gateState,
                    zoneId = gateViewModel.zoneId,
                    viewModel = sellGraphViewModel(navController, entry),
                    onOpenDay = openDay,
                    onCloseDay = closeDay,
                    onCheckout = { navController.navigate(CheckoutRoute) },
                    onAddProduct = { navController.navigate(EditProductRoute()) },
                    onScan = { navController.navigate(ScanSellRoute) { launchSingleTop = true } },
                )
            }
            composable<ScanSellRoute> { entry ->
                val sellViewModel = sellGraphViewModel(navController, entry)
                // Fails closed: the scanner only runs while the day is open (a closing day pops it).
                if (gateState is DayGateState.Ready) {
                    ScanHost(
                        continuous = true,
                        onKnown = { product -> sellViewModel.addOne(product.id) },
                        onAddProduct = { code -> navController.navigate(EditProductRoute(barcode = code)) },
                        onDone = { navController.popBackStack() },
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable<CheckoutRoute> { entry ->
                CheckoutScreen(
                    viewModel = sellGraphViewModel(navController, entry),
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable<SalesRoute> {
            SalesScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
                onOpenSale = { navController.navigate(SaleDetailRoute(it)) },
            )
        }
        composable<SaleDetailRoute> {
            SaleDetailScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = { navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } },
                onRestoreFromCloud = { navController.navigate(CloudSignInRoute("RESTORE")) },
            )
        }
        composable<OpenDayRoute> {
            OpenDayScreen(
                isExpanded = isExpanded,
                onDayStarted = {
                    // 6.4: Start the day lands on Home, whatever screen Open the day was opened from.
                    if (!navController.popBackStack<HomeRoute>(inclusive = false)) {
                        navController.navigate(HomeRoute)
                    }
                },
            )
        }
        composable<StockRoute> {
            StockScreen(
                isExpanded = isExpanded,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onOpenDay = openDay,
                onCloseDay = closeDay,
                onNewProduct = { navController.navigate(EditProductRoute()) },
                onEditProduct = { navController.navigate(EditProductRoute(it)) },
                onAddPurchase = { navController.navigate(AddPurchaseRoute(productId = it)) },
                onScan = { navController.navigate(ScanOnceRoute) { launchSingleTop = true } },
                onAddScanned = { navController.navigate(EditProductRoute(barcode = it)) },
                onScanPurchase = { navController.navigate(ScanPurchaseRoute) { launchSingleTop = true } },
            )
        }
        composable<AddPurchaseRoute> {
            AddPurchaseScreen(
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onOpenDay = openDay,
                onCloseDay = closeDay,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                onScan = { navController.navigate(ScanPurchaseRoute) { launchSingleTop = true } },
            )
        }
        // Continuous scanner for Record purchase. The purchase form lives on the entry that opened the scanner
        // (AddPurchaseRoute on phones, StockRoute on tablets), so its PurchaseViewModel is that entry's.
        composable<ScanPurchaseRoute> { entry ->
            // Pinned once: while Done pops this entry, previousBackStackEntry can already point elsewhere.
            val host = remember(entry) { navController.previousBackStackEntry }
            // Fails closed: no purchase host, or the day is not open (a closing day pops it), means no scanner.
            if (host != null && host.isPurchaseHost() && gateState is DayGateState.Ready) {
                val purchaseViewModel: PurchaseViewModel = hiltViewModel(host)
                ScanHost(
                    continuous = true,
                    onKnown = { product -> purchaseViewModel.addOrIncrement(product.id) },
                    onAddProduct = { code -> navController.navigate(EditProductRoute(barcode = code)) },
                    onDone = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable<EditProductRoute> {
            EditProductScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
                onScan = { navController.navigate(ScanOnceRoute) { launchSingleTop = true } },
            )
        }
        // Single-shot scanner: hands the first code back to the screen below it (Edit product, Stock).
        composable<ScanOnceRoute> {
            ScanOnceHost(
                onCode = { code ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_BARCODE_KEY, code)
                    navController.popBackStack()
                },
                onDone = { navController.popBackStack() },
            )
        }
        composable<DayRoute> {
            DayScreen(
                isExpanded = isExpanded,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onOpenDay = openDay,
                onCloseDay = closeDay,
                onRecordMoney = { navController.navigate(RecordMoneyRoute(it.name)) },
                onDebtors = { navController.navigate(DebtorsRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RecordMoneyRoute> {
            RecordMoneyScreen(
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onOpenDay = openDay,
                onCloseDay = closeDay,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable<CloseDayRoute> {
            CloseDayScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
                onDebtors = { navController.navigate(DebtorsRoute) },
                onClosed = { dayId, sent ->
                    AutoBackupWorker.enqueue(context)
                    navController.navigate(DayClosedRoute(dayId, sent)) { popUpTo<CloseDayRoute> { inclusive = true } }
                },
            )
        }
        composable<DayClosedRoute> { entry ->
            DayClosedScreen(
                summarySent = entry.toRoute<DayClosedRoute>().sent,
                zoneId = gateViewModel.zoneId,
                onDone = {
                    if (!navController.popBackStack<HomeRoute>(inclusive = false)) navController.navigate(HomeRoute)
                },
            )
        }
        composable<YesterdayOpenRoute> {
            YesterdayOpenScreen(
                zoneId = gateViewModel.zoneId,
                onCloseDay = { navController.navigate(CloseDayRoute(it)) },
            )
        }
        composable<DebtorsRoute> {
            DebtorsScreen(
                isExpanded = isExpanded,
                zoneId = gateViewModel.zoneId,
                nowMillis = { System.currentTimeMillis() },
                onBack = { navController.popBackStack() },
                onRecordPayment = { customerId ->
                    navController.navigate(RecordMoneyRoute(MoneyEntryType.CREDIT_REPAYMENT.name, customerId))
                },
            )
        }
        composable<SuppliersRoute> {
            SuppliersScreen(
                isExpanded = isExpanded,
                onOpenSupplier = { navController.navigate(SupplierDetailRoute(it)) },
                onNewSupplier = { navController.navigate(SupplierEditRoute()) },
                onEditSupplier = { navController.navigate(SupplierEditRoute(it)) },
                onRecordPurchase = recordPurchase,
            )
        }
        composable<SupplierDetailRoute> {
            SupplierDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(SupplierEditRoute(it)) },
                onRecordPurchase = recordPurchase,
            )
        }
        composable<SupplierEditRoute> {
            SupplierEditScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ReportsRoute> {
            ReportsScreen(
                isExpanded = isExpanded,
                onOpenDay = { navController.navigate(DaySummaryRoute(it)) },
                onDebtors = { navController.navigate(DebtorsRoute) },
            )
        }
        composable<DaySummaryRoute> {
            PastDayScreen(
                isExpanded = isExpanded,
                zoneId = gateViewModel.zoneId,
                onBack = { navController.popBackStack() },
                onSales = { navController.navigate(SalesRoute(it)) },
            )
        }
        composable<CloudPayRoute> {
            CloudPayScreen(onDone = { navController.popBackStack() })
        }
        composable<CloudRestoreRoute> {
            CloudRestoreScreen(onBack = { navController.popBackStack() })
        }
        composable<CloudSignInRoute> { entry ->
            val restoring = entry.toRoute<CloudSignInRoute>().purpose == "RESTORE"
            CloudSignInScreen(
                onDone = { navController.popBackStack() },
                onLinked = {
                    if (restoring) {
                        navController.navigate(CloudRestoreRoute) { popUpTo<CloudSignInRoute> { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = settingsViewModel,
                isExpanded = isExpanded,
                onNavigateBack = if (!isExpanded) { { navController.popBackStack() } } else null,
                onCloudSignIn = { navController.navigate(CloudSignInRoute()) },
                onCloudPay = { navController.navigate(CloudPayRoute) },
                onCloudRestore = { navController.navigate(CloudRestoreRoute) },
                onManageStaff = { navController.navigate(StaffRoute) { launchSingleTop = true } },
            )
        }
        composable<StaffRoute> {
            StaffScreen(onBack = { navController.popBackStack<StaffRoute>(true) })
        }
    }
}

/** Rail, bottom bar and Home's tiles: each tab keeps its own saved back stack. */
internal fun NavHostController.navigateToTab(dest: NavDestination) {
    // Close the day and Day closed are one-off steps: leaving them through the rail drops them
    // instead of saving them, so a finished day never comes back as a stale Day closed screen.
    val inDayFlow = currentBackStack.value.any {
        it.destination.hasRoute(CloseDayRoute::class) || it.destination.hasRoute(DayClosedRoute::class)
    }
    // N1: owner-only screens (restore, cloud, staff) are never saved, so a tab tap by the next person can't bring them back.
    val saveStack = !inDayFlow && !currentStackHoldsGated()
    // Home is the start destination: go back to it rather than restore a stack saved for it.
    if (dest == NavDestination.HOME) {
        if (!popBackStack<HomeRoute>(inclusive = false, saveState = saveStack) &&
            currentDestination?.hasRoute(HomeRoute::class) != true
        ) {
            navigate(HomeRoute) { launchSingleTop = true }
        }
        return
    }
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = saveStack
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Tablet Record purchase (7.5) switches to the Stock tab. Pushed onto Suppliers instead, Stock sat in the
 * Suppliers stack, and tapping Suppliers in the rail restored that same stack, so the tap did nothing.
 */
internal fun NavHostController.openStockForSupplier(supplierId: Long) {
    // Stock's saved stack has its own supplier, or none; drop it so this one is preselected.
    clearBackStack<StockRoute>()
    navigate(StockRoute(supplierId)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** The cart lives in one SellViewModel scoped to the Sell graph, shared by Sell and Checkout. */
@Composable
private fun sellGraphViewModel(navController: NavHostController, entry: NavBackStackEntry): SellViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry<SellGraph>() }
    return hiltViewModel(graphEntry)
}

/** The two screens that show the purchase form (phone Add purchase, tablet Stock); only they may open the purchase scanner. */
internal fun NavBackStackEntry.isPurchaseHost(): Boolean =
    destination.hasRoute<AddPurchaseRoute>() || destination.hasRoute<StockRoute>()
