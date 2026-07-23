package com.cosyra.app.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBitrateControllerTest {

    @Test
    fun severeLossReducesBitrateImmediately() {
        val controller = AdaptiveBitrateController(initialBitrateBps = 4_000_000)

        val bitrate = controller.update(
            NetworkQualitySample(packetLossFraction = 0.20, roundTripTimeMs = 80)
        )

        assertEquals(2_480_000, bitrate)
    }

    @Test
    fun healthySamplesIncreaseBitrateOnlyAfterStabilityWindow() {
        val controller = AdaptiveBitrateController(initialBitrateBps = 4_000_000)
        val healthy = NetworkQualitySample(packetLossFraction = 0.0, roundTripTimeMs = 40)

        assertEquals(4_000_000, controller.update(healthy))
        assertEquals(4_000_000, controller.update(healthy))
        assertEquals(4_400_000, controller.update(healthy))
    }

    @Test
    fun availableBandwidthCapsTarget() {
        val controller = AdaptiveBitrateController(initialBitrateBps = 8_000_000)

        val bitrate = controller.update(
            NetworkQualitySample(
                packetLossFraction = 0.0,
                roundTripTimeMs = 30,
                availableOutgoingBitrateBps = 2_500_000
            )
        )

        assertEquals(2_500_000, bitrate)
    }

    @Test
    fun bitrateNeverDropsBelowMinimum() {
        val controller = AdaptiveBitrateController(initialBitrateBps = 500_000)

        repeat(5) {
            controller.update(NetworkQualitySample(packetLossFraction = 1.0, roundTripTimeMs = 1_000))
        }

        assertTrue(controller.targetBitrateBps >= AdaptiveBitrateController.DEFAULT_MIN_BITRATE_BPS)
    }
}
