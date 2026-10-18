package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraConnectionUiStateTest {
    @Test fun disconnectAndMissingSubscriptionRemainDistinct() {
        val empty = NoraConnectionUiState.resolve(false, null, null, false)
        val ready = NoraConnectionUiState.resolve(false, null, null, true)
        assertEquals(NoraConnectionPhase.NO_SUBSCRIPTION, empty.phase)
        assertEquals(NoraConnectionPhase.DISCONNECTED, ready.phase)
        assertFalse(empty.canToggle)
        assertTrue(ready.canToggle)
    }

    @Test fun nativeServiceStateAlwaysWinsOverButtonExpectation() {
        assertEquals(NoraConnectionPhase.CONNECTING,
            NoraConnectionUiState.resolve(false, true, null, true).phase)
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionUiState.resolve(true, true, null, true).phase)
        assertEquals(NoraConnectionPhase.DISCONNECTING,
            NoraConnectionUiState.resolve(true, false, null, true).phase)
        assertEquals(NoraConnectionPhase.DISCONNECTED,
            NoraConnectionUiState.resolve(false, false, null, true).phase)
        assertFalse(NoraConnectionUiState.resolve(false, true, null, true).canToggle)
    }

    @Test fun timeoutIsUnconfirmedNotFalselyReportedAsConnectedOrDisconnected() {
        val start = NoraConnectionUiState.resolve(false, null, true, true)
        val stop = NoraConnectionUiState.resolve(true, null, false, true)
        assertEquals(NoraConnectionPhase.CONNECTION_TIMEOUT, start.phase)
        assertEquals(NoraConnectionPhase.DISCONNECTION_TIMEOUT, stop.phase)
        assertTrue(start.statusLabel.contains("تأیید نشده"))
        assertTrue(stop.statusLabel.contains("تأیید نشده"))
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionUiState.resolve(true, null, true, true).phase)
        assertEquals(NoraConnectionPhase.DISCONNECTED,
            NoraConnectionUiState.resolve(false, null, false, true).phase)
    }

    @Test fun activeTunnelCanAlwaysRequestDisconnectEvenWithoutAnAvailableRoute() {
        val active = NoraConnectionUiState.resolve(true, null, null, false)
        assertEquals(NoraConnectionPhase.CONNECTED, active.phase)
        assertTrue(active.canToggle)
    }
}
