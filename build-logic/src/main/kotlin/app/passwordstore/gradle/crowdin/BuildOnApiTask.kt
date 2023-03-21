package app.passwordstore.gradle.crowdin

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This calls into a remote API and has nothing to cache")
abstract class BuildOnApiTask : DefaultTask() {

  @get:Input abstract val crowdinIdentifier: Property<String>
  @get:Internal abstract val crowdinLogin: Property<String>
  @get:Internal abstract val crowdinKey: Property<String>

  @TaskAction
  fun doWork() {
    val client =
      OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()
    val url =
      CROWDIN_BUILD_API_URL.format(crowdinIdentifier.get(), crowdinLogin.get(), crowdinKey.get())
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().close()
  }

  private companion object {

    private const val CROWDIN_BUILD_API_URL =
      "https://api.crowdin.com/api/project/%s/export?login=%s&account-key=%s"
  }
}
