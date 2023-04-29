@file:Suppress("Unused")

package org.slf4j.impl

import app.passwordstore.util.log.LogcatLoggerFactory
import org.slf4j.ILoggerFactory
import org.slf4j.spi.LoggerFactoryBinder

class StaticLoggerBinder : LoggerFactoryBinder {
  private val loggerFactory: ILoggerFactory = LogcatLoggerFactory()
  private val loggerFactoryClassStr = LogcatLoggerFactory::javaClass.name

  override fun getLoggerFactory(): ILoggerFactory {
    return loggerFactory
  }

  override fun getLoggerFactoryClassStr(): String {
    return loggerFactoryClassStr
  }

  companion object {
    @JvmStatic val singleton = StaticLoggerBinder()
  }
}
