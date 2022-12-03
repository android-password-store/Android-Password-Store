package app.passwordstore.util.leaks

import leakcanary.EventListener

object SentryLeakUploader : EventListener {

  override fun onEvent(event: EventListener.Event) {}
}
