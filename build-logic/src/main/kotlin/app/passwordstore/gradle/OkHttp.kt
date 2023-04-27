package app.passwordstore.gradle

import java.util.concurrent.TimeUnit
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

object OkHttp {
  private val certificatePinner =
    CertificatePinner.Builder()
      .add(
        "api.crowdin.com",
        "sha256/qKpGqFXXIteblI82BcMyRX0eC2o7lpL9XVInWKIG7rc=",
        "sha256/DxH4tt40L+eduF6szpY6TONlxhZhBd+pJ9wbHlQ2fuw=",
        "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=",
      )
      .add(
        "publicsuffix.org",
        "sha256/GHmZgxELzHuqpSexbC20wv6kqtrqS6BFdKs0z5pciGw=",
        "sha256/cXjPgKdVe6iojP8s0YQJ3rtmDFHTnYZxcYvmYGFiYME=",
        "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
      )
      .build()
  val CLIENT =
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.MINUTES)
      .writeTimeout(5, TimeUnit.MINUTES)
      .readTimeout(5, TimeUnit.MINUTES)
      .callTimeout(10, TimeUnit.MINUTES)
      .certificatePinner(certificatePinner)
      .build()
}
