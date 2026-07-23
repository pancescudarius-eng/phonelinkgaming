package com.cosyra.app.webrtc

import kotlin.math.roundToInt

data class NetworkQualitySample(
    val packetLossFraction: Double,
    val roundTripTimeMs: Long,
    val availableOutgoingBitrateBps: Int? = null
)

class AdaptiveBitrateController(
    initialBitrateBps: Int = DEFAULT_INITIAL_BITRATE_BPS,
    private val minimumBitrateBps: Int = DEFAULT_MIN_BITRATE_BPS,
    private val maximumBitrateBps: Int = DEFAULT_MAX_BITRATE_BPS
) {
    var targetBitrateBps: Int = initialBitrateBps.coerceIn(minimumBitrateBps, maximumBitrateBps)
        private set

    private var healthySamples = 0

    fun update(sample: NetworkQualitySample): Int {
        val loss = sample.packetLossFraction.coerceIn(0.0, 1.0)
        val constrainedEstimate = sample.availableOutgoingBitrateBps
            ?.coerceIn(minimumBitrateBps, maximumBitrateBps)

        targetBitrateBps = when {
            loss >= SEVERE_PACKET_LOSS || sample.roundTripTimeMs >= SEVERE_RTT_MS -> {
                healthySamples = 0
                (targetBitrateBps * SEVERE_DECREASE_FACTOR).roundToInt()
            }
            loss >= MODERATE_PACKET_LOSS || sample.roundTripTimeMs >= MODERATE_RTT_MS -> {
                healthySamples = 0
                (targetBitrateBps * MODERATE_DECREASE_FACTOR).roundToInt()
            }
            else -> {
                healthySamples += 1
                if (healthySamples >= HEALTHY_SAMPLES_BEFORE_INCREASE) {
                    healthySamples = 0
                    (targetBitrateBps * INCREASE_FACTOR).roundToInt()
                } else {
                    targetBitrateBps
                }
            }
        }.coerceIn(minimumBitrateBps, maximumBitrateBps)

        if (constrainedEstimate != null && targetBitrateBps > constrainedEstimate) {
            targetBitrateBps = constrainedEstimate
            healthySamples = 0
        }
        return targetBitrateBps
    }

    fun reset(initialBitrateBps: Int = DEFAULT_INITIAL_BITRATE_BPS) {
        targetBitrateBps = initialBitrateBps.coerceIn(minimumBitrateBps, maximumBitrateBps)
        healthySamples = 0
    }

    companion object {
        const val DEFAULT_MIN_BITRATE_BPS = 500_000
        const val DEFAULT_INITIAL_BITRATE_BPS = 4_000_000
        const val DEFAULT_MAX_BITRATE_BPS = 16_000_000

        private const val MODERATE_PACKET_LOSS = 0.05
        private const val SEVERE_PACKET_LOSS = 0.12
        private const val MODERATE_RTT_MS = 180L
        private const val SEVERE_RTT_MS = 350L
        private const val MODERATE_DECREASE_FACTOR = 0.82
        private const val SEVERE_DECREASE_FACTOR = 0.62
        private const val INCREASE_FACTOR = 1.10
        private const val HEALTHY_SAMPLES_BEFORE_INCREASE = 3
    }
}
