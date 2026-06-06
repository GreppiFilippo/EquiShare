/** Defines app routes and navigation flow. */
package it.unibo.equishare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import it.unibo.equishare.domain.repository.AuthState
import it.unibo.equishare.ui.components.navigation.BottomNavDestination
import it.unibo.equishare.ui.components.navigation.EquiShareBottomBar
import it.unibo.equishare.ui.components.permissions.NotificationPermissionEffect
import it.unibo.equishare.ui.components.topbar.EquiShareAppBar
import it.unibo.equishare.ui.screens.activity.ActivityContent
import it.unibo.equishare.ui.screens.activity.ActivityEvent
import it.unibo.equishare.ui.screens.activity.ActivityViewModel
import it.unibo.equishare.ui.screens.expense.create.AddExpenseEvent
import it.unibo.equishare.ui.screens.expense.create.AddExpenseScreen
import it.unibo.equishare.ui.screens.expense.create.AddExpenseViewModel
import it.unibo.equishare.ui.screens.expense.details.ExpenseInfoEvent
import it.unibo.equishare.ui.screens.expense.details.ExpenseInfoScreen
import it.unibo.equishare.ui.screens.expense.details.ExpenseInfoUiState
import it.unibo.equishare.ui.screens.expense.details.ExpenseInfoViewModel
import it.unibo.equishare.ui.screens.groups.create.NewGroupEvent
import it.unibo.equishare.ui.screens.groups.create.NewGroupScreen
import it.unibo.equishare.ui.screens.groups.create.NewGroupViewModel
import it.unibo.equishare.ui.screens.groups.details.GroupDetailEvent
import it.unibo.equishare.ui.screens.groups.details.GroupDetailScreen
import it.unibo.equishare.ui.screens.groups.details.GroupDetailViewModel
import it.unibo.equishare.ui.screens.groups.list.GroupsContent
import it.unibo.equishare.ui.screens.groups.list.GroupsEvent
import it.unibo.equishare.ui.screens.groups.list.GroupsViewModel
import it.unibo.equishare.ui.screens.groups.settings.GroupSettingsEvent
import it.unibo.equishare.ui.screens.groups.settings.GroupSettingsScreen
import it.unibo.equishare.ui.screens.groups.settings.GroupSettingsViewModel
import it.unibo.equishare.ui.screens.login.LoginScreen
import it.unibo.equishare.ui.screens.login.LoginViewModel
import it.unibo.equishare.ui.screens.profile.ProfileEvent
import it.unibo.equishare.ui.screens.profile.ProfileScreen
import it.unibo.equishare.ui.screens.profile.ProfileViewModel
import it.unibo.equishare.ui.screens.signup.SignUpEvent
import it.unibo.equishare.ui.screens.signup.SignUpScreen
import it.unibo.equishare.ui.screens.signup.SignUpViewModel
import it.unibo.equishare.ui.screens.statistics.StatisticsContent
import it.unibo.equishare.ui.screens.statistics.StatisticsViewModel
import it.unibo.equishare.ui.notifications.NotificationNavigationTarget
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

sealed interface EquiShareRoute {
    @Serializable data object Login : EquiShareRoute
    @Serializable data object SignUp : EquiShareRoute
    @Serializable data object Groups : EquiShareRoute
    @Serializable data object Activity : EquiShareRoute
    @Serializable data object Statistics : EquiShareRoute
    @Serializable data object NewGroup : EquiShareRoute
    @Serializable data class GroupDetail(val groupId: String) : EquiShareRoute
    @Serializable data class GroupSettings(val groupId: String) : EquiShareRoute
    @Serializable data class AddExpense(val groupId: String) : EquiShareRoute
    @Serializable data class EditExpense(val expenseId: String) : EquiShareRoute
    @Serializable data class ExpenseInfo(val expenseId: String) : EquiShareRoute
    @Serializable data object Profile : EquiShareRoute
}

@Composable
fun EquiShareNavGraph(
    navController: NavHostController,
    notificationTarget: NotificationNavigationTarget? = null,
    onNotificationTargetConsumed: () -> Unit = {},
) {
    val mainVm = koinViewModel<MainViewModel>()
    val authState by mainVm.authState.collectAsStateWithLifecycle()
    val unreadCount by mainVm.unreadCount.collectAsStateWithLifecycle()
    val appBarState by mainVm.appBarState.collectAsStateWithLifecycle()

    if (authState == AuthState.LOADING) {
        Box(
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LaunchedEffect(authState, notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        if (authState == AuthState.SIGNED_IN) {
            navController.navigateToNotificationTarget(target)
            onNotificationTargetConsumed()
        }
    }
    
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val isOnAuthRoute =
        currentDestination?.hasRoute(EquiShareRoute.Login::class) == true ||
        currentDestination?.hasRoute(EquiShareRoute.SignUp::class) == true

    LaunchedEffect(authState, currentDestination) {
        if (authState == AuthState.SIGNED_OUT && currentDestination != null && !isOnAuthRoute) {
            navController.navigate(EquiShareRoute.Login) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val selectedTab = when {
        currentDestination?.hasRoute(EquiShareRoute.Activity::class) == true ->
            BottomNavDestination.ACTIVITY
        currentDestination?.hasRoute(EquiShareRoute.Statistics::class) == true ->
            BottomNavDestination.STATISTICS
        else ->
            BottomNavDestination.GROUPS
    }

    val showMainScaffoldChrome =
        currentDestination?.hasRoute(EquiShareRoute.Groups::class) == true ||
        currentDestination?.hasRoute(EquiShareRoute.Activity::class) == true ||
        currentDestination?.hasRoute(EquiShareRoute.Statistics::class) == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showMainScaffoldChrome) {
                EquiShareAppBar(
                    avatarUrl = appBarState.avatarUrl,
                    displayName = appBarState.displayName,
                    onAvatarClick = {
                        navController.navigate(EquiShareRoute.Profile) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
        bottomBar = {
            // Persistent across tab switches — only renders on tab routes, and
            // is kept *outside* the NavHost so it doesn't slide / fade with the
            // content. Tapping it just navigates the NavHost beneath.
            if (showMainScaffoldChrome) {
                EquiShareBottomBar(
                    selectedDestination = selectedTab,
                    onNavigateToDestination = { navigateToBottomTab(navController, it) },
                    showActivityBadge = unreadCount > 0 && selectedTab != BottomNavDestination.ACTIVITY,
                )
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = if (authState == AuthState.SIGNED_IN) {
                EquiShareRoute.Groups
            } else {
                EquiShareRoute.Login
            },
            modifier = Modifier.padding(scaffoldPadding),
            enterTransition    = { resolveEnterTransition() },
            exitTransition     = { resolveExitTransition() },
            popEnterTransition = { resolvePopEnterTransition() },
            popExitTransition  = { resolvePopExitTransition() },
        ) {
            loginRoute(navController)
            signUpRoute(navController)
            groupsRoute(navController)
            activityRoute(navController)
            statisticsRoute()
            newGroupRoute(navController)
            groupDetailRoute(navController)
            groupSettingsRoute(navController)
            addExpenseRoute(navController)
            editExpenseRoute(navController)
            expenseInfoRoute(navController)
            profileRoute(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual routes — extracted as NavGraphBuilder extension functions so the
// main graph reads as a clean table of contents.
// ─────────────────────────────────────────────────────────────────────────────

private fun NavGraphBuilder.loginRoute(navController: NavHostController) {
    composable<EquiShareRoute.Login> {
        val vm = koinViewModel<LoginViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(state.isLoggedIn) {
            if (state.isLoggedIn) {
                navController.navigate(EquiShareRoute.Groups) {
                    popUpTo(EquiShareRoute.Login) { inclusive = true }
                }
            }
        }

        LoginScreen(
            viewModel = vm,
            onSignUpClick = { navController.navigate(EquiShareRoute.SignUp) },
        )
    }
}

private fun NavGraphBuilder.signUpRoute(navController: NavHostController) {
    composable<EquiShareRoute.SignUp> {
        val vm = koinViewModel<SignUpViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()

        SignUpScreen(
            uiState = state,
            onEvent = { event ->
                when (event) {
                    SignUpEvent.BackClicked -> navController.popBackStack()
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun NavGraphBuilder.groupsRoute(navController: NavHostController) {
    composable<EquiShareRoute.Groups> {
        val vm = koinViewModel<GroupsViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()

        // First-launch-after-login system notification prompt. Lives here
        // rather than in LoginScreen because by the time the prompt should
        // appear the Login composable has already been popped from the back stack.
        NotificationPermissionEffect()

        GroupsContent(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    is GroupsEvent.GroupClicked ->
                        navController.navigate(EquiShareRoute.GroupDetail(event.groupId))
                    GroupsEvent.CreateGroupClicked ->
                        navController.navigate(EquiShareRoute.NewGroup)
                    is GroupsEvent.ToggleFavorite -> Unit // gestito dal ViewModel
                    is GroupsEvent.ReorderGroups -> Unit // gestito dal ViewModel
                }
                vm.onEvent(event)
            },
        )
    }
}

private fun NavGraphBuilder.activityRoute(navController: NavHostController) {
    composable<EquiShareRoute.Activity> {
        val vm = koinViewModel<ActivityViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(vm) {
            vm.navigation.collect { target ->
                navController.navigateToNotificationTarget(target)
            }
        }

        ActivityContent(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    is ActivityEvent.ActivityItemClicked ->
                        navController.navigateToNotificationTarget(event.target)
                    else -> { /* fall through to VM */ }
                }
                vm.onEvent(event)
            },
        )
    }
}

private fun NavGraphBuilder.statisticsRoute() {
    composable<EquiShareRoute.Statistics> {
        val vm = koinViewModel<StatisticsViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner, vm) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    vm.refreshSilently()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        StatisticsContent(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = vm::onEvent,
        )
    }
}

private fun NavGraphBuilder.newGroupRoute(navController: NavHostController) {
    composable<EquiShareRoute.NewGroup>(
        enterTransition    = { modalEnterTransition() },
        exitTransition     = { modalHoldExitTransition() },
        popEnterTransition = { modalHoldEnterTransition() },
        popExitTransition  = { modalExitTransition() },
    ) {
        val vm = koinViewModel<NewGroupViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.isCreated) {
            if (state.isCreated) {
                vm.consumeCreated()
                navController.popBackStack()
            }
        }

        NewGroupScreen(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    NewGroupEvent.BackClicked -> navController.popBackStack()
                    NewGroupEvent.CreateGroupClicked -> vm.onEvent(event)
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun NavGraphBuilder.groupDetailRoute(navController: NavHostController) {
    composable<EquiShareRoute.GroupDetail> { backStackEntry ->
        val route = backStackEntry.toRoute<EquiShareRoute.GroupDetail>()
        val vm = koinViewModel<GroupDetailViewModel>()
        LaunchedEffect(route.groupId) { vm.setGroupId(route.groupId) }
        val state by vm.uiState.collectAsStateWithLifecycle()

        GroupDetailScreen(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    GroupDetailEvent.BackClicked -> navController.popBackStack()
                    GroupDetailEvent.MoreOptionsClicked ->
                        navController.navigate(EquiShareRoute.GroupSettings(route.groupId))
                    GroupDetailEvent.AddExpenseClicked ->
                        navController.navigate(EquiShareRoute.AddExpense(route.groupId))
                    is GroupDetailEvent.ExpenseClicked ->
                        navController.navigate(EquiShareRoute.ExpenseInfo(event.expenseId))
                    is GroupDetailEvent.SettleDebtConfirmed -> Unit
                    GroupDetailEvent.SettlementFeedbackConsumed -> Unit
                }
                vm.onEvent(event)
            },
        )
    }
}

private fun NavGraphBuilder.groupSettingsRoute(navController: NavHostController) {
    composable<EquiShareRoute.GroupSettings> { backStackEntry ->
        val route = backStackEntry.toRoute<EquiShareRoute.GroupSettings>()
        val vm = koinViewModel<GroupSettingsViewModel>()
        LaunchedEffect(route.groupId) { vm.setGroupId(route.groupId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.isClosed) {
            if (state.isClosed) {
                vm.consumeClosed()
                navController.popBackStack(EquiShareRoute.Groups, inclusive = false)
            }
        }

        GroupSettingsScreen(
            uiState = state,
            feedback = vm.feedback,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    GroupSettingsEvent.BackClicked -> navController.popBackStack()
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun NavGraphBuilder.addExpenseRoute(navController: NavHostController) {
    composable<EquiShareRoute.AddExpense>(
        enterTransition    = { modalEnterTransition() },
        exitTransition     = { modalHoldExitTransition() },
        popEnterTransition = { modalHoldEnterTransition() },
        popExitTransition  = { modalExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<EquiShareRoute.AddExpense>()
        val vm = koinViewModel<AddExpenseViewModel>()
        LaunchedEffect(route.groupId) { vm.setGroupId(route.groupId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.isSaved) {
            if (state.isSaved) {
                vm.consumeSaved()
                navController.popBackStack()
            }
        }

        AddExpenseScreen(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    AddExpenseEvent.BackClicked -> navController.popBackStack()
                    AddExpenseEvent.SaveClicked -> vm.onEvent(event)
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun NavGraphBuilder.expenseInfoRoute(navController: NavHostController) {
    composable<EquiShareRoute.ExpenseInfo> { backStackEntry ->
        val route = backStackEntry.toRoute<EquiShareRoute.ExpenseInfo>()
        val vm = koinViewModel<ExpenseInfoViewModel>()
        LaunchedEffect(route.expenseId) { vm.setExpenseId(route.expenseId) }
        val state by vm.uiState.collectAsStateWithLifecycle()

        state?.let { ui ->
            ExpenseInfoScreen(
                uiState = ui,
                onRefresh = vm::refresh,
                onEvent = { event ->
                    when (event) {
                        ExpenseInfoEvent.BackClicked -> navController.popBackStack()
                        ExpenseInfoEvent.DeleteClicked -> {
                            if (ui.canModifyExpense) {
                                vm.onEvent(event)
                                navController.popBackStack()
                            }
                        }
                        ExpenseInfoEvent.EditClicked -> {
                            if (ui.canModifyExpense) {
                                navController.navigate(EquiShareRoute.EditExpense(route.expenseId))
                            }
                        }
                        else -> vm.onEvent(event)
                    }
                },
            )
        } ?: ExpenseInfoScreen(uiState = ExpenseInfoUiState(), onEvent = {})
    }
}

private fun NavGraphBuilder.editExpenseRoute(navController: NavHostController) {
    composable<EquiShareRoute.EditExpense>(
        enterTransition    = { modalEnterTransition() },
        exitTransition     = { modalHoldExitTransition() },
        popEnterTransition = { modalHoldEnterTransition() },
        popExitTransition  = { modalExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<EquiShareRoute.EditExpense>()
        val vm = koinViewModel<AddExpenseViewModel>()
        LaunchedEffect(route.expenseId) { vm.setEditExpenseId(route.expenseId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.isSaved) {
            if (state.isSaved) {
                vm.consumeSaved()
                navController.popBackStack()
            }
        }

        AddExpenseScreen(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    AddExpenseEvent.BackClicked -> navController.popBackStack()
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun NavGraphBuilder.profileRoute(navController: NavHostController) {
    composable<EquiShareRoute.Profile> {
        val vm = koinViewModel<ProfileViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()

        ProfileScreen(
            uiState = state,
            onRefresh = vm::refresh,
            onEvent = { event ->
                when (event) {
                    ProfileEvent.BackClicked -> navController.popBackStack()
                    ProfileEvent.LogOutClicked -> vm.onEvent(event)
                    else -> vm.onEvent(event)
                }
            },
        )
    }
}

private fun navigateToBottomTab(
    navController: NavHostController,
    destination: BottomNavDestination,
) {
    val route: EquiShareRoute = when (destination) {
        BottomNavDestination.GROUPS     -> EquiShareRoute.Groups
        BottomNavDestination.ACTIVITY   -> EquiShareRoute.Activity
        BottomNavDestination.STATISTICS -> EquiShareRoute.Statistics
    }
    navController.navigate(route) {
        popUpTo(EquiShareRoute.Groups) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToNotificationTarget(
    target: NotificationNavigationTarget,
) {
    when (target) {
        NotificationNavigationTarget.ActivityCenter ->
            navigateToBottomTab(this, BottomNavDestination.ACTIVITY)
        is NotificationNavigationTarget.GroupDetail ->
            navigate(EquiShareRoute.GroupDetail(target.groupId)) {
                launchSingleTop = true
            }
        is NotificationNavigationTarget.ExpenseInfo ->
            navigate(EquiShareRoute.ExpenseInfo(target.expenseId)) {
                launchSingleTop = true
            }
    }
}
