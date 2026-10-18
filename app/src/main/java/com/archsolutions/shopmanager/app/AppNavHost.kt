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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.archsolutions.shopmanager.feature.settings.SettingsScreen
import com.archsolutions.shopmanager.feature.settings.SettingsViewModel
import com.archsolutions.shopmanager.feature.stock.AddPurchaseScreen
import com.archsolutions.shopmanager.feature.stock.EditProductScreen
import com.archsolutions.shopmanager.feature.stock.StockScreen
import com.archsolutions.shopmanager.feature.suppliers.SuppliersScreen
import kotlinx.serialization.Serializable

// Type-safe route objects
@Serializable object HomeRoute
@Serializable object SellRoute
@Serializable object StockRoute
@Serializable object DayRoute
@Serializable object SuppliersRoute
@Serializable object ReportsRoute
@Serializable object SettingsRoute
@Serializable object OpenDayRoute
@Serializable object SellGraph // Sell + phone Checkout share one SellViewModel (the cart)
@Serializable object CheckoutRoute
@Serializable object SalesRoute
@Serializable data class SaleDetailRoute(val id: Long)
@Serializable data class AddPurchaseRoute(val productId: Long = 0L, val supplierId: Long = 0L) // 0 = none
@Serializable data class EditProductRoute(val id: Long = 0L) // 0 = new product
@Serializable data class RecordMoneyRoute(val type: String = "EXPENSE", val customerId: Long = 0L) // MoneyEntryType name
@Serializable object DebtorsRoute
@Serializable data class CloseDayRoute(val date: String)
@Serializable data class DayClosedRoute(val dayId: Long, val sent: Boolean = false)
@Serializable data class YesterdayOpenRoute(val date: String)

enum class NavDestination(
    val route: Any,
    val labelResId: Int,
    val icon: ImageVector,
    val showInCompactBottomBar: Boolean = true,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Outlined.Home),
    SELL(SellRoute, R.string.nav_sell, Icons.Outlined.ShoppingCart),
    STOCK(StockRoute, R.string.nav_stock, Icons.Outlined.Inventory2),
    DAY(DayRoute, R.string.nav_day, Icons.Outlined.AccountBalanceWallet, showInCompactBottomBar = false),
    SUPPLIERS(SuppliersRoute, R.string.nav_suppliers, Icons.Outlined.LocalShipping),
    REPORTS(ReportsRoute, R.string.nav_reports, Icons.Outlined.BarChart),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings, showInCompactBottomBar = false),
}

@Composable
fun AppNavHost(
    isExpanded: Boolean,
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

    if (gateState == DayGateState.Loading) {
        Box(modifier = modifier.fillMaxSize().background(colors.paper))
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Gate steps are full-screen on every layout (no rail on the design boards).
    val isOpenDay = currentRoute == OpenDayRoute::class.qualifiedName ||
        currentDestination?.hasRoute(YesterdayOpenRoute::class) == true
    // Hold gate prompts while a day is being opened or closed: Open the day pushed over Close the day
    // was popped along with it, so closing yesterday never led on to opening today.
    val canPrompt = currentDestination != null && !isOpenDay &&
        !currentDestination.hasRoute(CloseDayRoute::class) && !currentDestination.hasRoute(DayClosedRoute::class)

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
    fun isSelected(dest: NavDestination): Boolean {
        val destRouteName = dest.route::class.qualifiedName
        return currentRoute == destRouteName
    }

    fun navigateTo(dest: NavDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (isExpanded) {
        // Expanded layout (Tablet): Navigation Rail (104 dp wide) + content
        Row(modifier = modifier.fillMaxSize().background(colors.paper)) {
            // Open the day is a full-screen task on tablet too (no rail on the design board)
            if (!isOpenDay) {
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
                                    text = "M",
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
                            onClick = { navigateTo(dest) },
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
                                    fontSize = 13.sp,
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
                        onClick = { navigateTo(NavDestination.SETTINGS) },
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
                                fontSize = 13.sp,
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
                    isExpanded = true,
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
            destination?.hasRoute(DayClosedRoute::class) == true

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.paper,
            bottomBar = {
                if (!isFullscreenTask) {
                    // 76 dp of items above the system navigation inset, which NavigationBar pads inside itself.
                    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    NavigationBar(
                        modifier = Modifier.height(76.dp + systemNavInset),
                        containerColor = colors.surface,
                    ) {
                        bottomNavDestinations.forEach { dest ->
                            val selected = isSelected(dest)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
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
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
                )
            }
        }
    }
}

@Composable
private fun NavContent(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    gateViewModel: DayGateViewModel,
    gateState: DayGateState,
    isExpanded: Boolean,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val today by gateViewModel.today.collectAsStateWithLifecycle()
    val openDay = { navController.navigate(OpenDayRoute) { launchSingleTop = true } }
    // Closes the day the gate is on: today's, or the earlier day still waiting to be closed.
    val closeDay = {
        gateState.currentDay?.date?.let { date -> navController.navigate(CloseDayRoute(date)) { launchSingleTop = true } }
        Unit
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
            HomeScreen(
                isExpanded = isExpanded,
                shopName = settings.shopName,
                today = today,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onNavigateToSell = { navController.navigate(SellRoute) },
                onNavigateToStock = { navController.navigate(StockRoute) },
                onAddExpense = {
                    if (isExpanded) navController.navigate(DayRoute) else navController.navigate(RecordMoneyRoute())
                },
                onCloseDay = closeDay,
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onOpenDay = openDay,
                recentSales = recentSales,
                lowStock = lowStock,
                onSeeAllSales = { navController.navigate(SalesRoute) },
                onOpenSale = { navController.navigate(SaleDetailRoute(it)) },
                todaySummary = todaySummary,
                onDaySummary = { navController.navigate(DayClosedRoute(it)) },
                onReopenDay = homeViewModel::reopenDay,
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
                )
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
            )
        }
        composable<EditProductRoute> {
            EditProductScreen(
                isExpanded = isExpanded,
                onBack = { navController.popBackStack() },
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
            SuppliersScreen(isExpanded = isExpanded)
        }
        composable<ReportsRoute> {
            ReportsScreen(isExpanded = isExpanded)
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = settingsViewModel,
                isExpanded = isExpanded,
                onNavigateBack = if (!isExpanded) { { navController.popBackStack() } } else null,
            )
        }
    }
}

/** The cart lives in one SellViewModel scoped to the Sell graph, shared by Sell and Checkout. */
@Composable
private fun sellGraphViewModel(navController: NavHostController, entry: NavBackStackEntry): SellViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry<SellGraph>() }
    return hiltViewModel(graphEntry)
}
