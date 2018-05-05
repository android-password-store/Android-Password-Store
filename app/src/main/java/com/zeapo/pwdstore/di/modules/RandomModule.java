package com.zeapo.pwdstore.di.modules;

import com.zeapo.pwdstore.pwgen.PRNGFixes;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RandomModule {
//    @Singleton
    @Provides
    SecureRandom provideSecureRandom() {
        try {
            PRNGFixes.apply();
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }
}
