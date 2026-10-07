package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.H264SdpOrder
import io.github.vandosketch.camgrid.core.SignalingException
import kotlinx.io.IOException

/**
 * The signalling half of a WebRTC player whose peer connection lives outside Kotlin (the iOS
 * app's Swift player): reorders the offer's H264 profiles for go2rtc ([H264SdpOrder]), POSTs it
 * with [WhepClient] and returns the answer, or a short failure code that is safe to show and log.
 */
class WebRtcOfferExchange(private val whep: WhepClient) {

    sealed interface Result {
        data class Answer(val sdp: String) : Result

        data class Failed(val reason: String) : Result
    }

    suspend fun exchange(url: String, offerSdp: String): Result {
        // Configs saved before the editor normalised URLs may hold a go2rtc player page URL.
        val endpoint = Go2rtc.webrtcEndpoint(url) ?: url
        return try {
            Result.Answer(whep.exchange(endpoint, H264SdpOrder.apply(offerSdp)))
        } catch (e: SignalingException) {
            Result.Failed(e.code)
        } catch (e: IOException) {
            // WhepClient's IOException messages are safe: "Invalid URL" or the failure's type.
            Result.Failed(e.message ?: "IOException")
        }
    }
}
