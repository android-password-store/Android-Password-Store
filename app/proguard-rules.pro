# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-dontwarn java.lang.invoke.*
-dontwarn org.eclipse.jgit.**
-dontwarn com.google.common.**
-dontwarn org.slf4j.**
-keep class androidx.appcompat.widget.SearchView { *; }
-keepattributes SourceFile,LineNumberTable
-dontobfuscate
-keep class com.jcraft.jsch.**
-keep class org.eclipse.jgit.internal.JGitText { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class !org.bouncycastle.jce.provider.X509LDAPCertStoreSpi { *; }

# Tink (for security-crypto)
# I'm most certainly not a fan of this catch-all rule
-keep class com.google.crypto.tink.proto.** { *; }

# WhatTheStack
-keep class com.haroldadmin.whatthestack.WhatTheStackInitializer {
  <init>();
}
