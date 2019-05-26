package com.zeapo.pwdstore.utils

fun String.splitLines(): Array<String> {
    return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}
