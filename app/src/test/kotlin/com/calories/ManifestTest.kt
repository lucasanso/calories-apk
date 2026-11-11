package com.calories

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestTest {
    @Test
    fun `app nao pede permissao de internet nem de rede`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse("android.permission.INTERNET" in requested, "Permissões pedidas: $requested")
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in requested, "Permissões pedidas: $requested")
    }
}
