/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package me.msfjarvis.openpgpktx.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.openintents.openpgp.IOpenPgpService2

public class OpenPgpServiceConnection(context: Context, providerPackageName: String?) {

  // callback interface
  public interface OnBound {

    public fun onBound(service: IOpenPgpService2)
    public fun onError(e: Exception)
  }

  private val mApplicationContext: Context = context.applicationContext
  public var service: IOpenPgpService2? = null
    private set
  private val mProviderPackageName: String? = providerPackageName
  private var mOnBoundListener: OnBound? = null

  /**
   * Create new connection with callback
   *
   * @param context
   * @param providerPackageName specify package name of OpenPGP provider, e.g.,
   * "org.sufficientlysecure.keychain"
   * @param onBoundListener callback, executed when connection to service has been established
   */
  public constructor(
    context: Context,
    providerPackageName: String?,
    onBoundListener: OnBound?
  ) : this(context, providerPackageName) {
    mOnBoundListener = onBoundListener
  }

  public val isBound: Boolean
    get() = service != null

  private val mServiceConnection: ServiceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        this@OpenPgpServiceConnection.service = IOpenPgpService2.Stub.asInterface(service)
        mOnBoundListener?.onBound(this@OpenPgpServiceConnection.service!!)
      }

      override fun onServiceDisconnected(name: ComponentName) {
        service = null
      }
    }

  /** If not already bound, bind to service! */
  public fun bindToService() {
    if (service == null) {
      // if not already bound...
      try {
        val serviceIntent = Intent(OpenPgpApi.SERVICE_INTENT_2)
        // NOTE: setPackage is very important to restrict the intent to this provider only!
        serviceIntent.setPackage(mProviderPackageName)
        val connect = mApplicationContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        if (!connect) {
          throw Exception("bindService() returned false!")
        }
      } catch (e: Exception) {
        mOnBoundListener?.onError(e)
      }
    } else {
      // already bound, but also inform client about it with callback
      mOnBoundListener?.onBound(service!!)
    }
  }

  public fun unbindFromService() {
    mApplicationContext.unbindService(mServiceConnection)
  }
}
