/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.util.Patterns
import kotlinx.coroutines.runBlocking
import mozilla.components.lib.publicsuffixlist.PublicSuffixList

private object PublicSuffixListCache {
    private lateinit var publicSuffixList: PublicSuffixList

    fun getOrCachePublicSuffixList(context: Context): PublicSuffixList {
        if (!::publicSuffixList.isInitialized) {
            publicSuffixList = PublicSuffixList(context)
            // Trigger loading the actual public suffix list, but don't block.
            @Suppress("DeferredResultUnused")
            publicSuffixList.prefetch()
        }
        return publicSuffixList
    }
}

fun cachePublicSuffixList(context: Context) {
    PublicSuffixListCache.getOrCachePublicSuffixList(context)
}

/**
 * Returns the eTLD+1 (also called registrable domain), i.e. the direct subdomain of the public
 * suffix of [domain].
 *
 * Note: Invalid domains, such as IP addresses, are returned unchanged and thus never collide with
 * the return value for valid domains.
 */
fun getPublicSuffixPlusOne(context: Context, domain: String) = runBlocking {
    // We only feed valid domain names which are not IP addresses into getPublicSuffixPlusOne.
    // We do not check whether the domain actually exists (actually, not even whether its TLD
    // exists). As long as we restrict ourselves to syntactically valid domain names,
    // getPublicSuffixPlusOne will return non-colliding results.
    if (!Patterns.DOMAIN_NAME.matcher(domain).matches() || Patterns.IP_ADDRESS.matcher(domain)
            .matches()
    ) {
        domain
    } else {
        PublicSuffixListCache.getOrCachePublicSuffixList(context).getPublicSuffixPlusOne(domain)
            .await() ?: domain
    }
}
