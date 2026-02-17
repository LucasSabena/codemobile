package com.codemobile.terminal.di

import com.codemobile.terminal.TerminalBootstrap
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {

    // TerminalBootstrap is @Singleton @Inject so Hilt auto-provides it.
    // This module can host additional terminal-related providers.
}
