package com.archsolutions.shopmanager.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.feature.day.DayScreen
import com.archsolutions.shopmanager.feature.day.OpenDayScreen
import com.archsolutions.shopmanager.feature.home.HomeScreen
import com.archsolutions.shopmanager.feature.reports.ReportsScreen
import com.archsolutions.shopmanager.feature.sell.SellScreen
import com.archsolutions.shopmanager.feature.settings.SettingsScreen
import com.archsolutions.shopmanager.feature.settings.SettingsViewModel
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

    LaunchedEffect(gateState) {
        if (gateViewModel.consumeOpenDayPrompt()) {
            navController.navigate(OpenDayRoute) { launchSingleTop = true }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOpenDay = currentRoute == OpenDayRoute::class.qualifiedName

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

        val isFullscreenTask = currentRoute == SettingsRoute::class.qualifiedName || isOpenDay

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.paper,
            bottomBar = {
                if (!isFullscreenTask) {
                    NavigationBar(
                        modifier = Modifier.height(76.dp),
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
    // DECISION: Close the day arrives in M6; until then the lock card's Close button opens the Day screen.
    val closeDay = { navController.navigate(DayRoute) { launchSingleTop = true } }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                isExpanded = isExpanded,
                shopName = settings.shopName,
                today = today,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onNavigateToSell = { navController.navigate(SellRoute) },
                onNavigateToStock = { navController.navigate(StockRoute) },
                onNavigateToDay = { navController.navigate(DayRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onOpenDay = openDay,
            )
        }
        composable<SellRoute> {
            SellScreen(
                isExpanded = isExpanded,
                gateState = gateState,
                zoneId = gateViewModel.zoneId,
                onOpenDay = openDay,
                onCloseDay = closeDay,
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
            StockScreen(isExpanded = isExpanded)
        }
        composable<DayRoute> {
            DayScreen(isExpanded = isExpanded)
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
