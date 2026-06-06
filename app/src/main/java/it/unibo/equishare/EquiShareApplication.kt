/** Initializes application-wide dependencies. */
package it.unibo.equishare

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EquiShareApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@EquiShareApplication)
            modules(appModule)
        }
    }
}
