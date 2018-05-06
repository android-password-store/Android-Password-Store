package com.zeapo.pwdstore.di.modules;

import com.zeapo.pwdstore.crypto.password.fragments.PwgenDialogFragment;
import com.zeapo.pwdstore.crypto.ssh.fragments.ShowSshKeyFragment;
import com.zeapo.pwdstore.crypto.ssh.fragments.SshKeyGenFragment;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class FragmentModule {
    @ContributesAndroidInjector(modules = {RandomModule.class})
    abstract PwgenDialogFragment providePwgenDialogFragment();
}
