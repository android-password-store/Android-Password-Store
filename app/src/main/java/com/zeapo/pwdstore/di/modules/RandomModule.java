package com.zeapo.pwdstore.di.modules;

import com.zeapo.pwdstore.crypto.password.PRNGFixes;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import dagger.Module;
import dagger.Provides;

@Module
public class RandomModule {
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
