package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallUiPolicyTest {
    @Test
    fun `visible RED activity keeps incoming call inside the unified overlay`() {
        assertFalse(IncomingCallUiPolicy.shouldLaunchIncomingActivity(hasResumedRedActivity = true))
    }

    @Test
    fun `background RED process launches lockscreen incoming activity`() {
        assertTrue(IncomingCallUiPolicy.shouldLaunchIncomingActivity(hasResumedRedActivity = false))
    }

    // ── P0 2026-09-27: شاشة الرنين تتبع مالك الحالة (CallRuntime) بدل مؤقتها وحده ──

    @Test
    fun `caller cancelling closes the ring screen`() {
        val ended = CallUiState.CallEnded("peer", "VOICE", 0L, "call-1")
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(ended, "call-1"))
    }

    @Test
    fun `answered elsewhere closes the ring screen`() {
        val active = CallUiState.Active("call-1", "peer", "VOICE", 1L)
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(active, "call-1"))
    }

    @Test
    fun `call waiting on another call closes the ring screen`() {
        val active = CallUiState.Active("call-1", "peer", "VOICE", 1L)
        val waiting = CallUiState.Incoming("call-2", "other", "VOICE")
        assertTrue(
            IncomingCallUiPolicy.shouldDismissRingScreen(
                CallUiState.ActiveWithIncoming(active, waiting),
                "call-2"
            )
        )
    }

    @Test
    fun `a newer incoming call replaces the stale ring screen`() {
        val ringing = CallUiState.Incoming("call-NEW", "peer", "VOICE")
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(ringing, "call-OLD"))
    }

    @Test
    fun `the same ringing call keeps the ring screen`() {
        val ringing = CallUiState.Incoming("call-1", "peer", "VOICE")
        assertFalse(IncomingCallUiPolicy.shouldDismissRingScreen(ringing, "call-1"))
    }

    @Test
    fun `push wake before the offer arrives keeps the ring screen`() {
        assertFalse(IncomingCallUiPolicy.shouldDismissRingScreen(CallUiState.Idle, "call-1"))
    }

    @Test
    fun `connecting and reconnecting keep the ring screen`() {
        val connecting = CallUiState.Connecting("call-1", "peer", "VOICE")
        val reconnecting = CallUiState.Reconnecting("call-1", "peer", "VOICE")
        assertFalse(IncomingCallUiPolicy.shouldDismissRingScreen(connecting, "call-1"))
        assertFalse(IncomingCallUiPolicy.shouldDismissRingScreen(reconnecting, "call-1"))
    }

    @Test
    fun `terminal outcomes close the ring screen`() {
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(CallUiState.Busy("peer"), "call-1"))
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(CallUiState.Declined("peer"), "call-1"))
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(CallUiState.NoAnswer("peer", outgoing = false), "call-1"))
        assertTrue(IncomingCallUiPolicy.shouldDismissRingScreen(CallUiState.Error("boom"), "call-1"))
    }
}
