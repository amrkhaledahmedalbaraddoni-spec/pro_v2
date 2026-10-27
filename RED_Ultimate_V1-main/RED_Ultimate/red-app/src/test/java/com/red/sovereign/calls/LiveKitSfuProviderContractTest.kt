package com.red.sovereign.calls

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد LiveKitSfuProvider الاحتياطي (2026-09-26):
 * مسار الإنتاج هو Mediasoup، وهذا المزود يبقى آلة حالة صادقة
 * (fail-fast إلى FAILED) حتى تهبط إضافة LiveKit SDK.
 */
class LiveKitSfuProviderContractTest {

    @Test fun `starts disconnected and detached`() {
        val p = LiveKitSfuProvider()
        assertEquals(LiveKitRoomState.DISCONNECTED, p.roomState)
        assertFalse(p.isAttached())
    }

    @Test fun `join without SDK fails fast with reason`() = runBlocking {
        val p = LiveKitSfuProvider()
        assertFalse(p.join("room-1"))
        assertEquals(LiveKitRoomState.FAILED, p.roomState)
        assertEquals("livekit-sdk-not-wired", p.lastFailureReason)
        assertFalse(p.isAttached())
    }

    @Test fun `leave resets to disconnected`() = runBlocking {
        val p = LiveKitSfuProvider()
        p.join("room-1")
        p.leave()
        assertEquals(LiveKitRoomState.DISCONNECTED, p.roomState)
        assertEquals(null, p.lastFailureReason)
    }

    @Test fun `publish and mic camera are safe no-ops while not connected`() = runBlocking {
        val p = LiveKitSfuProvider()
        assertFalse(p.publish(CallMediaKind.CONFERENCE))
        p.setMicrophoneEnabled(true)
        p.setCameraEnabled(false)
        p.restartIce()
        assertFalse(p.isAttached())
    }

    @Test fun `subscribe rejects blank peer`() = runBlocking {
        val p = LiveKitSfuProvider()
        assertFalse(p.subscribe(""))
        assertFalse(p.subscribe("   "))
    }

    @Test fun `factory defaults to mediasoup`() {
        assertFalse(SfuProviderFactory.USE_LIVEKIT)
        assertEquals("mediasoup", SfuProviderFactory.activeName())
    }
}
