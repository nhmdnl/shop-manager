package com.archsolutions.shopmanager.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navigation
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Rail / bottom-bar tabs each keep one saved back stack, and Home's tiles share it.
@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {

    private fun navController(): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        nav.setLifecycleOwner(owner)
        nav.setViewModelStore(ViewModelStore())
        // Same shape as AppNavHost's graph, for the destinations these tests visit.
        nav.graph = nav.createGraph(startDestination = HomeRoute) {
            composable<HomeRoute> {}
            navigation<SellGraph>(startDestination = SellRoute) {
                composable<SellRoute> {}
                composable<CheckoutRoute> {}
            }
            composable<StockRoute> {}
            composable<EditProductRoute> {}
            composable<SuppliersRoute> {}
            composable<SupplierDetailRoute> {}
        }
        return nav
    }

    @Test
    fun `the cart survives a switch to another tab and back`() {
        val nav = navController()
        nav.navigateToTab(NavDestination.SELL)
        // The cart's SellViewModel lives in this entry; a new entry means an empty cart.
        val cartEntry = nav.getBackStackEntry<SellGraph>().id

        nav.navigateToTab(NavDestination.STOCK)
        nav.navigateToTab(NavDestination.SELL)

        assertTrue(nav.currentDestination!!.hasRoute(SellRoute::class))
        assertEquals(cartEntry, nav.getBackStackEntry<SellGraph>().id)
    }

    @Test
    fun `a tab opened from a Home tile does not come back as an older stack`() {
        val nav = navController()
        nav.navigateToTab(NavDestination.STOCK)
        nav.navigate(EditProductRoute(5))
        nav.navigateToTab(NavDestination.HOME)

        nav.navigateToTab(NavDestination.STOCK) // Home "Purchase" tile
        val fromTile = nav.currentBackStackEntry!!.id
        nav.navigateToTab(NavDestination.SUPPLIERS)
        nav.navigateToTab(NavDestination.STOCK)

        assertEquals(fromTile, nav.currentBackStackEntry!!.id)
    }

    @Test
    fun `after Record purchase on tablet, Suppliers goes back to the supplier`() {
        val nav = navController()
        nav.navigateToTab(NavDestination.SUPPLIERS)
        nav.navigate(SupplierDetailRoute(7))

        nav.openStockForSupplier(3)
        val purchase = nav.currentBackStackEntry!!
        assertEquals(3L, purchase.toRoute<StockRoute>().supplierId)

        nav.navigateToTab(NavDestination.SUPPLIERS)
        assertEquals(7L, nav.currentBackStackEntry!!.toRoute<SupplierDetailRoute>().id)

        // The purchase being typed in is still there on the way back.
        nav.navigateToTab(NavDestination.STOCK)
        assertEquals(purchase.id, nav.currentBackStackEntry!!.id)
    }

    @Test
    fun `Record purchase replaces what the Stock tab had open`() {
        val nav = navController()
        nav.navigateToTab(NavDestination.STOCK)
        nav.navigate(EditProductRoute(5))
        nav.navigateToTab(NavDestination.SUPPLIERS)

        nav.openStockForSupplier(3)
        nav.navigateToTab(NavDestination.SUPPLIERS)
        nav.navigateToTab(NavDestination.STOCK)

        assertEquals(3L, nav.currentBackStackEntry!!.toRoute<StockRoute>().supplierId)
        assertFalse(nav.currentBackStack.value.any { it.destination.hasRoute(EditProductRoute::class) })
    }
}
