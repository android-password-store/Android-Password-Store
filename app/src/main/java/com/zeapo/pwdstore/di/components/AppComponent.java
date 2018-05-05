package com.zeapo.pwdstore.di.components;

import android.app.Application;

import com.zeapo.pwdstore.PasswordStoreApplication;
import com.zeapo.pwdstore.di.builder.ActivityBuilder;
import com.zeapo.pwdstore.di.modules.FragmentModule;
import com.zeapo.pwdstore.di.modules.RandomModule;
import com.zeapo.pwdstore.di.modules.RandomPasswordGeneratorModule;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjectionModule;

@Singleton
@Component(modules = {AndroidInjectionModule.class, ActivityBuilder.class})
public interface AppComponent {
    void inject(PasswordStoreApplication app);

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder application(Application application);

        AppComponent build();
    }
}
