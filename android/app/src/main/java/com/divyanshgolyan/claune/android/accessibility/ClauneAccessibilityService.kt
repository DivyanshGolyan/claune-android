package com.divyanshgolyan.claune.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.divyanshgolyan.claune.android.app.clauneContainer

class ClauneAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = clauneContainer()
        container.accessibilityBridge.attach(this)
        container.sessionCoordinator.setAccessibilityConnected(true)
        container.sessionCoordinator.logEvent("Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        clauneContainer().accessibilityBridge.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        clauneContainer().sessionCoordinator.logEvent("Accessibility service interrupted by the system.")
    }

    override fun onDestroy() {
        clauneContainer().accessibilityBridge.detach()
        clauneContainer().sessionCoordinator.setAccessibilityConnected(false)
        super.onDestroy()
    }
}
