package app.passwordstore.gradle.ktfmt

import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

abstract class KtfmtFormatTask
@Inject
constructor(
  private val workerExecutor: WorkerExecutor,
  private val projectLayout: ProjectLayout,
) : SourceTask() {

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun execute() {
    val result =
      with(workerExecutor.noIsolation()) {
        submit(KtfmtWorkerAction::class.java) {
          name.set("foofoo")
          files.from(source)
          projectDirectory.set(projectLayout.projectDirectory.asFile)
        }
        runCatching { await() }
      }

    result.exceptionOrNull()?.workErrorCauses<Exception>()?.ifNotEmpty {
      forEach { logger.error(it.message, it.cause) }
      throw GradleException("error formatting sources for $name")
    }
  }

  private inline fun <reified T : Throwable> Throwable.workErrorCauses(): List<Throwable> {
    return when (this) {
        is MultiCauseException -> this.causes.map { it.cause }
        else -> listOf(this.cause)
      }
      .filter {
        // class instance comparison doesn't work due to different classloaders
        it?.javaClass?.canonicalName == T::class.java.canonicalName
      }
      .filterNotNull()
  }

  companion object {

    private const val PARALLEL_TASK_LIMIT = 4
  }
}
