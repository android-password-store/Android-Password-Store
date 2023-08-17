package dev.msfjarvis.tracing

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
public class TracingCommandLineProcessor : CommandLineProcessor {

  override val pluginId: String = "dev.msfjarvis.tracing"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
