/** Defines dependency injection bindings for the app. */
package it.unibo.equishare

import it.unibo.equishare.data.local.AppLanguageManager
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.local.EquiShareDatabase
import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.remote.GoogleSignInHelper
import it.unibo.equishare.data.remote.createSupabase
import it.unibo.equishare.data.remote.FcmTokenSyncService
import it.unibo.equishare.data.remote.datasource.SupabaseAuthDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseActivityDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseDeviceTokenDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseExpensesDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseGroupsDataSource
import it.unibo.equishare.data.remote.datasource.SupabasePaymentsDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseProfileDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseStatisticsDataSource
import it.unibo.equishare.data.repositories.SupabaseActivityRepository
import it.unibo.equishare.data.repositories.SupabaseAuthRepository
import it.unibo.equishare.data.repositories.SupabaseDeviceTokenRepository
import it.unibo.equishare.data.repositories.SupabaseExpensesRepository
import it.unibo.equishare.data.repositories.SupabaseGroupsRepository
import it.unibo.equishare.data.repositories.SupabasePaymentsRepository
import it.unibo.equishare.data.repositories.SupabaseProfileRepository
import it.unibo.equishare.data.repositories.SupabaseStatisticsRepository
import it.unibo.equishare.domain.repository.ActivityRepository
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.DeviceTokenRepository
import it.unibo.equishare.domain.repository.ExpensesRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import it.unibo.equishare.domain.repository.PaymentsRepository
import it.unibo.equishare.domain.repository.ProfileRepository
import it.unibo.equishare.domain.repository.StatisticsRepository
import it.unibo.equishare.domain.usecase.AddExpenseUseCase
import it.unibo.equishare.domain.usecase.CreateGroupUseCase
import it.unibo.equishare.domain.usecase.InviteMemberUseCase
import it.unibo.equishare.domain.usecase.RespondToInviteUseCase
import it.unibo.equishare.data.remote.RealtimeNotificationService
import it.unibo.equishare.ui.MainViewModel
import it.unibo.equishare.ui.notifications.NotificationManager
import it.unibo.equishare.ui.notifications.SystemNotificationManager
import it.unibo.equishare.ui.screens.activity.ActivityViewModel
import it.unibo.equishare.ui.screens.expense.create.AddExpenseViewModel
import it.unibo.equishare.ui.screens.expense.details.ExpenseInfoViewModel
import it.unibo.equishare.ui.screens.groups.create.NewGroupViewModel
import it.unibo.equishare.ui.screens.groups.details.GroupDetailViewModel
import it.unibo.equishare.ui.screens.groups.list.GroupsViewModel
import it.unibo.equishare.ui.screens.groups.settings.GroupSettingsViewModel
import it.unibo.equishare.ui.screens.login.LoginViewModel
import it.unibo.equishare.ui.screens.profile.ProfileViewModel
import it.unibo.equishare.ui.screens.signup.SignUpViewModel
import it.unibo.equishare.ui.screens.statistics.StatisticsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { createSupabase() }
    single { GoogleSignInHelper() }
    single { UserPreferencesDataSource(androidContext()) }
    single { AppLanguageManager(androidContext(), get()) }
    single { EquiShareDatabase.create(androidContext()) }
    single { EquiShareLocalDataSource(get()) }
    single<NotificationManager> { SystemNotificationManager(androidContext()) }
    single(createdAtStart = true) { RealtimeNotificationService(get(), get(), get(), get()) }
    single(createdAtStart = true) { FcmTokenSyncService(get(), get()) }

    single { SupabaseAuthDataSource(get()) }
    single { SupabaseGroupsDataSource(get()) }
    single { SupabaseExpensesDataSource(get()) }
    single { SupabaseActivityDataSource(get()) }
    single { SupabaseProfileDataSource(get()) }
    single { SupabasePaymentsDataSource(get()) }
    single { SupabaseStatisticsDataSource(get()) }
    single { SupabaseDeviceTokenDataSource(get()) }

    single<AuthRepository> { SupabaseAuthRepository(get(), get(), get()) }
    single<GroupsRepository> { SupabaseGroupsRepository(get(), get(), get(), get()) }
    single<ActivityRepository> { SupabaseActivityRepository(get(), get(), get(), get(), get()) }
    single<ProfileRepository> { SupabaseProfileRepository(get(), get(), get(), get(), get()) }
    single<ExpensesRepository> { SupabaseExpensesRepository(get(), get(), get()) }
    single<PaymentsRepository> { SupabasePaymentsRepository(get(), get()) }
    single<DeviceTokenRepository> { SupabaseDeviceTokenRepository(get(), get()) }
    single<StatisticsRepository> { SupabaseStatisticsRepository(get(), get()) }

    factory { AddExpenseUseCase(get(), get()) }
    factory { CreateGroupUseCase(get()) }
    factory { InviteMemberUseCase(get()) }
    factory { RespondToInviteUseCase(get()) }

    viewModel { MainViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { SignUpViewModel(get(), get()) }
    viewModel { GroupsViewModel(get(), get(), get()) }
    viewModel { ActivityViewModel(get(), get(), get()) }
    viewModel { StatisticsViewModel(get(), get()) }
    viewModel { NewGroupViewModel(get(), get()) }
    viewModel { GroupDetailViewModel(get(), get(), get(), get()) }
    viewModel { GroupSettingsViewModel(get(), get()) }
    viewModel { AddExpenseViewModel(get(), get(), get(), get(), get()) }
    viewModel { ExpenseInfoViewModel(get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
}
