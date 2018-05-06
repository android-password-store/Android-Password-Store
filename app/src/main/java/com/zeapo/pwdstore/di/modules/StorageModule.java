package com.zeapo.pwdstore.di.modules;

import com.jcraft.jsch.JSch;

import dagger.Module;
import dagger.Provides;

@Module
public class StorageModule {
    @Provides
    JSch providesJSch() {
        return new JSch();
    }
}
