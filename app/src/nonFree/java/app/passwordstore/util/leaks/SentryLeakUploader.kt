package app.passwordstore.util.leaks

import io.sentry.Sentry
import io.sentry.SentryEvent
import leakcanary.EventListener
import leakcanary.EventListener.Event
import shark.HeapAnalysisSuccess
import shark.Leak
import shark.LeakTrace
import shark.LibraryLeak

object SentryLeakUploader : EventListener {

  override fun onEvent(event: Event) {
    when (event) {
      is Event.HeapAnalysisDone<*> -> {
        if (event.heapAnalysis !is HeapAnalysisSuccess) return
        val heapAnalysis = event.heapAnalysis as HeapAnalysisSuccess
        val allLeakTraces =
          heapAnalysis.allLeaks.toList().flatMap { leak ->
            leak.leakTraces.map { leakTrace -> leak to leakTrace }
          }

        allLeakTraces.forEach { (leak, leakTrace) ->
          val exception = FakeReportingException(leak.shortDescription)
          val sentryEvent =
            SentryEvent(exception).apply {
              val leakContexts = mutableMapOf<String, Any>()
              addHeapAnalysis(heapAnalysis, leakContexts)
              addLeak(leak, leakContexts)
              addLeakTrace(leakTrace, leakContexts)
              contexts["Leak"] = leakContexts
              // grouping leaks
              fingerprints = listOf(leak.signature)
            }
          Sentry.captureEvent(sentryEvent)
        }
      }
      else -> {}
    }
  }

  private fun addHeapAnalysis(
    heapAnalysis: HeapAnalysisSuccess,
    leakContexts: MutableMap<String, Any>
  ) {
    leakContexts["heapDumpPath"] = heapAnalysis.heapDumpFile.absolutePath
    heapAnalysis.metadata.forEach { (key, value) -> leakContexts[key] = value }
    leakContexts["analysisDurationMs"] = heapAnalysis.analysisDurationMillis
  }

  private fun addLeak(leak: Leak, leakContexts: MutableMap<String, Any>) {
    leakContexts["libraryLeak"] = leak is LibraryLeak
    if (leak is LibraryLeak) {
      leakContexts["libraryLeakPattern"] = leak.pattern.toString()
      leakContexts["libraryLeakDescription"] = leak.description
    }
  }

  private fun addLeakTrace(leakTrace: LeakTrace, leakContexts: MutableMap<String, Any>) {
    leakTrace.retainedHeapByteSize?.let { leakContexts["retainedHeapByteSize"] = it }
    leakContexts["signature"] = leakTrace.signature
    leakContexts["leakTrace"] = leakTrace.toString()
  }

  class FakeReportingException(message: String) : RuntimeException(message)
}
