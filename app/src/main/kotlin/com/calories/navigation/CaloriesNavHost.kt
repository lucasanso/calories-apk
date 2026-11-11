package com.calories.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calories.feature.data.DataRoute as DataScreen
import com.calories.feature.foodpicker.FoodPickerRoute as FoodPickerScreen
import com.calories.feature.foods.FoodDetailRoute as FoodDetailScreen
import com.calories.feature.foods.FoodEditorRoute as FoodEditorScreen
import com.calories.feature.foods.FoodsRoute as FoodsScreen
import com.calories.feature.history.HistoryRoute as HistoryScreen
import com.calories.feature.planning.PlanEditorRoute as PlanEditorScreen
import com.calories.feature.planning.PlannedDayRoute as PlannedDayScreen
import com.calories.feature.planning.PlanningRoute as PlanningScreen
import com.calories.feature.settings.EnergyCalculatorRoute as EnergyCalculatorScreen
import com.calories.feature.settings.GoalsRoute as GoalsScreen
import com.calories.feature.settings.MealSlotsRoute as MealSlotsScreen
import com.calories.feature.settings.SettingsRoute as SettingsScreen
import com.calories.feature.settings.SourcesRoute as SourcesScreen
import com.calories.feature.shopping.ShoppingListRoute as ShoppingListScreen
import com.calories.feature.today.TodayRoute as TodayScreen
import kotlin.reflect.KClass

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(TodayRoute(), TodayRoute::class, "Hoje", Icons.Outlined.Today),
    TopLevel(FoodsRoute, FoodsRoute::class, "Alimentos", Icons.Outlined.Restaurant),
    TopLevel(PlanningRoute, PlanningRoute::class, "Planejar", Icons.Outlined.CalendarMonth),
    TopLevel(HistoryRoute, HistoryRoute::class, "Histórico", Icons.Outlined.Insights),
    TopLevel(SettingsRoute, SettingsRoute::class, "Ajustes", Icons.Outlined.Settings),
)

@Composable
fun CaloriesNavHost(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = destination == null || TOP_LEVEL.any { t -> destination.hierarchy.any { it.hasRoute(t.routeClass) } }

    Scaffold(
        bottomBar = {
            if (showBottomBar) NavigationBar {
                TOP_LEVEL.forEach { t ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = t.routeClass != TodayRoute::class }
                                launchSingleTop = true
                                restoreState = t.routeClass != TodayRoute::class
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TodayRoute(),
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { EnterTransition.None + fadeIn() },
            popExitTransition = { ExitTransition.None + fadeOut() },
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onAddFood = { date, slotId -> nav.navigate(FoodPickerRoute(date.toEpochDay(), slotId)) },
                    onOpenFood = { nav.navigate(FoodDetailRoute(it)) },
                    onOpenGoals = { nav.navigate(GoalsRoute) },
                    onOpenPlanned = { nav.navigate(PlannedDayRoute(it.toEpochDay())) },
                )
            }
            composable<FoodsRoute> {
                FoodsScreen(onOpenFood = { nav.navigate(FoodDetailRoute(it)) }, onNewFood = { nav.navigate(FoodEditorRoute()) })
            }
            composable<PlanningRoute> {
                PlanningScreen(
                    onOpenDay = { nav.navigate(PlannedDayRoute(it.toEpochDay())) },
                    onOpenPlan = { nav.navigate(PlanEditorRoute(it)) },
                    onOpenList = { nav.navigate(ShoppingListRoute(it)) },
                )
            }
            composable<PlannedDayRoute> {
                PlannedDayScreen(
                    onBack = { nav.popBackStack() },
                    onAddFood = { date, slotId -> nav.navigate(FoodPickerRoute(date.toEpochDay(), slotId, PickTarget.PLANNED_DAY)) },
                    onOpenDiary = { date -> nav.navigate(TodayRoute(date.toEpochDay())) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } } },
                )
            }
            composable<PlanEditorRoute> {
                PlanEditorScreen(
                    onBack = { nav.popBackStack() },
                    onAddFood = { planId, slotId -> nav.navigate(FoodPickerRoute(0, slotId, PickTarget.PLAN, planId)) },
                    onOpenPlan = { id -> nav.navigate(PlanEditorRoute(id)) { popUpTo<PlanEditorRoute> { inclusive = true } } },
                )
            }
            composable<ShoppingListRoute> { ShoppingListScreen(onBack = { nav.popBackStack() }) }
            composable<HistoryRoute> {
                HistoryScreen(onOpenDay = { date ->
                    nav.navigate(TodayRoute(date.toEpochDay())) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
                })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onGoals = { nav.navigate(GoalsRoute) },
                    onMealSlots = { nav.navigate(MealSlotsRoute) },
                    onData = { nav.navigate(DataRoute) },
                    onSources = { nav.navigate(SourcesRoute) },
                )
            }
            composable<FoodPickerRoute> { FoodPickerScreen(onBack = { nav.popBackStack() }) }
            composable<FoodDetailRoute> {
                FoodDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(FoodEditorRoute(it)) },
                    onOpenCopy = { id -> nav.navigate(FoodEditorRoute(id)) { popUpTo<FoodDetailRoute> { inclusive = true } } },
                )
            }
            composable<FoodEditorRoute> {
                FoodEditorScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate(FoodDetailRoute(id)) { popUpTo<FoodEditorRoute> { inclusive = true } } },
                )
            }
            composable<GoalsRoute> { GoalsScreen(onBack = { nav.popBackStack() }, onCalculator = { nav.navigate(EnergyCalculatorRoute) }) }
            composable<EnergyCalculatorRoute> { EnergyCalculatorScreen(onBack = { nav.popBackStack() }) }
            composable<MealSlotsRoute> { MealSlotsScreen(onBack = { nav.popBackStack() }) }
            composable<SourcesRoute> { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable<DataRoute> { DataScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
