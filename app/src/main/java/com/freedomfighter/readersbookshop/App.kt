package com.freedomfighter.readersbookshop

import android.app.Application
import com.freedomfighter.readersbookshop.data.Downloads
import com.freedomfighter.readersbookshop.data.Prefs
import com.freedomfighter.readersbookshop.data.Shelf
import com.freedomfighter.readersbookshop.data.Storage
import com.freedomfighter.readersbookshop.sources.Registry

class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val storage: Storage by lazy { Storage(this) }
    val shelf: Shelf by lazy { Shelf(this) }
    val downloads: Downloads by lazy { Downloads(this, shelf, storage) }
    val registry: Registry by lazy { Registry(this, prefs) }
    override fun onCreate() { super.onCreate(); shelf.settle() }
}
