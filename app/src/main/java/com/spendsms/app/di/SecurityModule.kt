package com.spendsms.app.di

import com.spendsms.app.platform.security.KeystorePassphraseStore
import com.spendsms.app.platform.security.SecurePassphraseStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecurePassphraseStore(
        impl: KeystorePassphraseStore,
    ): SecurePassphraseStore
}
