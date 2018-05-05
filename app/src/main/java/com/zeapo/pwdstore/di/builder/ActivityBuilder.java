package com.zeapo.pwdstore.di.builder;

import com.zeapo.pwdstore.crypto.PgpActivity;
import com.zeapo.pwdstore.di.modules.FragmentModule;
import com.zeapo.pwdstore.di.modules.RandomModule;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ActivityBuilder {
    @ContributesAndroidInjector(modules = {FragmentModule.class, RandomModule.class})
    abstract PgpActivity pgpActivity();
}
