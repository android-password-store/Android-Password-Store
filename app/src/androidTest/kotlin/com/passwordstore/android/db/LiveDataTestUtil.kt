package com.passwordstore.android.db;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

fun <T> LiveData<T>.blockingObserve(): T? {
        var value: T? = null
        val latch = CountDownLatch(1)

        val observer = Observer<T> { t ->
        value = t
        latch.countDown()
        }

        observeForever(observer)

        latch.await(2, TimeUnit.SECONDS)
        return value
}