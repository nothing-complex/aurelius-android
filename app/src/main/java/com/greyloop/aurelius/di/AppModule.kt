package com.greyloop.aurelius.di

import com.greyloop.aurelius.data.local.AppDatabase
import com.greyloop.aurelius.data.remote.ToolExecutor
import com.greyloop.aurelius.data.repository.ChatRepository
import com.greyloop.aurelius.data.repository.ChatRepositoryInterface
import com.greyloop.aurelius.data.security.SecureStorage
import com.greyloop.aurelius.domain.usecase.CreateChatUseCase
import com.greyloop.aurelius.domain.usecase.DeleteChatUseCase
import com.greyloop.aurelius.domain.usecase.GetChatUseCase
import com.greyloop.aurelius.domain.usecase.GetChatsUseCase
import com.greyloop.aurelius.domain.usecase.GetMessagesUseCase
import com.greyloop.aurelius.domain.usecase.SendMessageUseCase
import com.greyloop.aurelius.domain.usecase.UpdateChatUseCase
import com.greyloop.aurelius.domain.usecase.BranchChatUseCase
import com.greyloop.aurelius.domain.usecase.GenerateSummaryUseCase
import com.greyloop.aurelius.ui.chat.ChatViewModel
import com.greyloop.aurelius.ui.home.HomeViewModel
import com.greyloop.aurelius.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Security
    single { SecureStorage(androidContext()) }

    // Database - lazy to defer Room initialization off the main thread
    single(createdAtStart = false) { AppDatabase.getInstance(androidContext()) }
    single(createdAtStart = false) { get<AppDatabase>().chatDao() }
    single(createdAtStart = false) { get<AppDatabase>().messageDao() }

    // Network
    factory { ToolExecutor(get()) }

    // Repository
    factory { ChatRepository(get(), get(), get(), get()) }
    factory<ChatRepositoryInterface> { ChatRepository(get(), get(), get(), get()) }

    // Use Cases
    factory { GetChatsUseCase(get()) }
    factory { GetChatUseCase(get()) }
    factory { GetMessagesUseCase(get()) }
    factory { CreateChatUseCase(get()) }
    factory { DeleteChatUseCase(get()) }
    factory { SendMessageUseCase(get()) }
    factory { UpdateChatUseCase(get()) }
    factory { BranchChatUseCase(get()) }
    factory { GenerateSummaryUseCase(get()) }

    // ViewModels
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { params -> ChatViewModel(params.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
