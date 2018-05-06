package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.Application;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;

import com.zeapo.pwdstore.di.components.DaggerAppComponent;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasFragmentInjector;
import dagger.android.HasServiceInjector;

public class PasswordStoreApplication extends Application implements HasActivityInjector, HasServiceInjector, HasFragmentInjector {
    private static PasswordStoreApplication context;

    @Inject
    DispatchingAndroidInjector<Activity> activityDispatchingAndroidInjector;
    @Inject
    DispatchingAndroidInjector<Service> serviceDispatchingAndroidInjector;
    @Inject
    DispatchingAndroidInjector<Fragment> dialogFragmentDispatchingAndroidInjector;

    public static Context getContext() {
        return context;
    }

    @Override
    public AndroidInjector<Activity> activityInjector() {
        return activityDispatchingAndroidInjector;
    }

    @Override
    public AndroidInjector<Service> serviceInjector() {
        return serviceDispatchingAndroidInjector;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        context = this;
        initialiseDependencyInjection();
    }

    protected void initialiseDependencyInjection() {
        DaggerAppComponent.builder()
                .application(this)
                .build()
                .inject(this);
    }

    @Override
    public AndroidInjector<Fragment> fragmentInjector() {
        return dialogFragmentDispatchingAndroidInjector;
    }
}
