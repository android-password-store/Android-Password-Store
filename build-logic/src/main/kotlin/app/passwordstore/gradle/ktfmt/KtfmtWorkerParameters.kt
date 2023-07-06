package app.passwordstore.gradle.ktfmt

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

interface KtfmtWorkerParameters : WorkParameters {
  val name: Property<String>
  val files: ConfigurableFileCollection
  val projectDirectory: RegularFileProperty
}
