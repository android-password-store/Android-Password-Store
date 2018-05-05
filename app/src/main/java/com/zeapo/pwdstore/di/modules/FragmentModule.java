package com.zeapo.pwdstore.di.modules;

import com.zeapo.pwdstore.PwgenDialogFragment;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class FragmentModule {
//    @FragmentScope
    @ContributesAndroidInjector(modules = {RandomModule.class})
    abstract PwgenDialogFragment providePwgenDialogFragment();
}
