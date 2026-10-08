package com.lstepnio.egauge

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider

/** Activity and optional monitor share exactly one BLE/coordinator owner. */
class EGaugeApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val model: AppViewModel get() = ViewModelProvider(this,ViewModelProvider.AndroidViewModelFactory.getInstance(this))[AppViewModel::class.java]
}
