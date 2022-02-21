-keepattributes SourceFile,LineNumberTable,EnclosingMethod
-dontobfuscate

-keep class com.jcraft.jsch.**
-keep class org.eclipse.jgit.internal.JGitText { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class !org.bouncycastle.jce.provider.X509LDAPCertStoreSpi,org.bouncycastle.jce.provider.** { *; }

-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn javax.servlet.ServletContextListener

# WhatTheStack
-keep class com.haroldadmin.whatthestack.WhatTheStackInitializer {
  <init>();
}
