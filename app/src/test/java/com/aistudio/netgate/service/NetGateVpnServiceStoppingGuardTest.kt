package com.aistudio.netgate.service

import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.WebsiteFilterMode
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetGateVpnServiceStoppingGuardTest {

    @Test
    fun `late publishRuntimeState cannot mark a stopping service as running`() {
        val service = NetGateVpnService()

        val stoppingField = NetGateVpnService::class.java.getDeclaredField("stopping")
        stoppingField.isAccessible = true
        stoppingField.setBoolean(service, true)

        val publish = NetGateVpnService::class.java.getDeclaredMethod(
            "publishRuntimeState",
            Boolean::class.javaPrimitiveType,
            ActiveNetworkType::class.java,
            Int::class.javaPrimitiveType,
            WebsiteFilterMode::class.java,
            Boolean::class.javaPrimitiveType
        )
        publish.isAccessible = true
        publish.invoke(service, true, ActiveNetworkType.WIFI, 0, WebsiteFilterMode.OFF, false)

        assertFalse(NetGateVpnService.runtimeState.value.isServiceRunning)
    }

    @Test
    fun `late network recompute cannot revive a stopping service`() {
        val service = NetGateVpnService()

        val stoppingField = NetGateVpnService::class.java.getDeclaredField("stopping")
        stoppingField.isAccessible = true
        stoppingField.setBoolean(service, true)

        val recompute = NetGateVpnService::class.java.getDeclaredMethod("recomputeNetworkAndRebuild")
        recompute.isAccessible = true
        recompute.invoke(service)

        assertFalse(NetGateVpnService.runtimeState.value.isServiceRunning)
    }
}