/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.github.androidpasswordstore.autofillparser

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

public fun cachePublicSuffixList(context: Context) {
    PublicSuffixListCache.getOrCachePublicSuffixList(context)
}

/**
 * Returns the eTLD+1 (also called registrable domain), i.e. the direct subdomain of the public
 * suffix of [domain].
 *
 * Note: Invalid domains, such as IP addresses, are returned unchanged and thus never collide with
 * the return value for valid domains.
 */
internal fun getPublicSuffixPlusOne(context: Context, domain: String, customSuffixes: Sequence<String>) = runBlocking {
    // We only feed valid domain names which are not IP addresses into getPublicSuffixPlusOne.
    // We do not check whether the domain actually exists (actually, not even whether its TLD
    // exists). As long as we restrict ourselves to syntactically valid domain names,
    // getPublicSuffixPlusOne will return non-colliding results.
    if (!Patterns.DOMAIN_NAME.matcher(domain).matches() || Patterns.IP_ADDRESS.matcher(domain)
            .matches()
    ) {
        domain
    } else {
        getCanonicalSuffix(context, domain, customSuffixes)
    }
}

/**
 * Returns:
 * - [domain], if [domain] equals [suffix];
 * - null, if [domain] does not have [suffix] as a domain suffix or only with an empty prefix;
 * - the direct subdomain of [suffix] of which [domain] is a subdomain.
 */
private fun getSuffixPlusUpToOne(domain: String, suffix: String): String? {
    if (domain == suffix)
        return domain
    val prefix = domain.removeSuffix(".$suffix")
    if (prefix == domain || prefix.isEmpty())
        return null
    val lastPrefixPart = prefix.takeLastWhile { it != '.' }
    return "$lastPrefixPart.$suffix"
}

private suspend fun getCanonicalSuffix(
    context: Context, domain: String, customSuffixes: Sequence<String>): String {
    val publicSuffixList = PublicSuffixListCache.getOrCachePublicSuffixList(context)
    val publicSuffixPlusOne = publicSuffixList.getPublicSuffixPlusOne(domain).await()
        ?: return domain
    var longestSuffix = publicSuffixPlusOne
    for (customSuffix in customSuffixes) {
        val suffixPlusUpToOne = getSuffixPlusUpToOne(domain, customSuffix) ?: continue
        // A shorter suffix is automatically a substring.
        if (suffixPlusUpToOne.length > longestSuffix.length)
            longestSuffix = suffixPlusUpToOne
    }
    return longestSuffix
}
