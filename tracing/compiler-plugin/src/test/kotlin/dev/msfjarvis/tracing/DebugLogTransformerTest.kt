package dev.msfjarvis.tracing

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class DebugLogTransformerTest {

  private val standardOut = System.out
  private val outputStreamCaptor = ByteArrayOutputStream()

  @After
  fun tearDown() {
    System.setOut(standardOut)
  }

  @Test
  fun `compiler plugin successfully transforms code`() {
    val srcFile =
      kotlin(
        "SourceFile.kt",
        """
      import dev.msfjarvis.tracing.runtime.annotations.DebugLog 
      
      @DebugLog
      fun transformable() {
        println("In a transformable function!")
      }
      
      @DebugLog
      fun transformableWithReturnValue(): String {
        println("In a transformable function!")
        return "Return value!"
      }
      
      fun nonTransformable() {
        println("Not in a transformable function!")
      }
      
      class TracingTest {
        @DebugLog
        fun transformableInClass() {
          println("In a transformable function!")
        }
        
        fun nonTransformable() {
          println("Not in a transformable function!")
        }
      }
    """
          .trimIndent()
      )

    val result =
      KotlinCompilation()
        .apply {
          sources = listOf(srcFile)
          compilerPluginRegistrars = listOf(TracingCompilerPluginRegistrar())
          commandLineProcessors = listOf(TracingCommandLineProcessor())
          noOptimize = true

          inheritClassPath = true
          messageOutputStream = System.out
        }
        .compile()
    assertEquals(ExitCode.OK, result.exitCode)

    System.setOut(PrintStream(outputStreamCaptor))
    val kClazz = result.classLoader.loadClass("SourceFileKt")
    val transformableWithReturnValue =
      kClazz.declaredMethods.first { it.name == "transformableWithReturnValue" }
    val retVal = transformableWithReturnValue.invoke(null)
    assertIs<String>(retVal)
    assertEquals("Return value!", retVal)
    assertEquals(
      """
        ⇢ transformableWithReturnValue()
        In a transformable function!
        ⇠ transformableWithReturnValue [] = Return value!
      """
        .trimIndent(),
      outputStreamCaptor.toString(StandardCharsets.UTF_8).trim().replace("\\[.*]".toRegex(), "[]"),
    )
  }
}
