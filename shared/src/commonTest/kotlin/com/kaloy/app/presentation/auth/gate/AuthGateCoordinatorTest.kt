package com.kaloy.app.presentation.auth.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthGateCoordinatorTest {

    @Test
    fun consommerActionEnAttenteRetirePuisRenvoieAction() {
        val coordinator = AuthGateCoordinator()
        var executions = 0
        val action: () -> Unit = { executions++ }

        coordinator.exigerAuthentification("écouter", action)

        val actionAExecuter = coordinator.consommerActionEnAttente()
        assertEquals(action, actionAExecuter)
        assertNull(coordinator.demande)
        actionAExecuter?.invoke()
        assertNull(coordinator.consommerActionEnAttente())
        assertEquals(1, executions)
    }

    @Test
    fun annulerEffaceActionEnAttente() {
        val coordinator = AuthGateCoordinator()
        coordinator.exigerAuthentification("écouter") {}

        coordinator.annuler()

        assertNull(coordinator.demande)
        assertNull(coordinator.consommerActionEnAttente())
    }
}
