package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraConnectionPhaseTest {
    @Test fun noRouteShowsSetupInsteadOfFalseConnectedState() {
        assertEquals(NoraConnectionPhase.NO_ROUTE,
            NoraConnectionPhase.resolve(isRunning = false, expectedRunning = null,
                hasRoute = false, timedOut = false))
    }

    @Test fun nativeServiceIsTheOnlySourceOfActiveState() {
        assertEquals(NoraConnectionPhase.READY,
            NoraConnectionPhase.resolve(false, null, true, false))
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionPhase.resolve(true, null, true, false))
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionPhase.resolve(true, null, false, false))
    }

    @Test fun pendingStartDoesNotClaimConnectionBeforeServiceStarts() {
        assertEquals(NoraConnectionPhase.CONNECTING,
            NoraConnectionPhase.resolve(false, true, true, false))
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionPhase.resolve(true, true, true, false))
    }

    @Test fun pendingStopKeepsServiceStatusHonest() {
        assertEquals(NoraConnectionPhase.DISCONNECTING,
            NoraConnectionPhase.resolve(true, false, true, false))
        assertEquals(NoraConnectionPhase.READY,
            NoraConnectionPhase.resolve(false, false, true, false))
    }

    @Test fun failedStartCanBeRetriedWithoutShowingFakeConnection() {
        assertEquals(NoraConnectionPhase.FAILED,
            NoraConnectionPhase.resolve(false, null, true, true))
        assertFalse(NoraConnectionPhase.FAILED.isBusy)
        assertEquals(NoraConnectionPhase.CONNECTED,
            NoraConnectionPhase.resolve(true, null, true, true))
    }

    @Test fun transitionStatesDisableConflictingPowerRequests() {
        assertTrue(NoraConnectionPhase.CONNECTING.isBusy)
        assertTrue(NoraConnectionPhase.DISCONNECTING.isBusy)
        assertFalse(NoraConnectionPhase.NO_ROUTE.isBusy)
        assertFalse(NoraConnectionPhase.CONNECTED.isBusy)
    }
}
