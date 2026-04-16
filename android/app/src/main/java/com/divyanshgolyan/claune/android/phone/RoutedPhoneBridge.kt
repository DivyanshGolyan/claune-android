package com.divyanshgolyan.claune.android.phone

import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ElementRef
import com.divyanshgolyan.claune.android.runtime.PhoneActuator
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScrollDirection
import com.divyanshgolyan.claune.android.runtime.UiSnapshot

enum class PhoneControlMode {
    LiveAccessibility,
    DemoPhone,
}

class RoutedPhoneBridge(
    private val liveObserver: PhoneObserver,
    private val liveActuator: PhoneActuator,
    private val demoBridge: DemoPhoneBridge,
) : PhoneObserver,
    PhoneActuator {
    @Volatile
    private var mode: PhoneControlMode = PhoneControlMode.LiveAccessibility

    fun setMode(mode: PhoneControlMode) {
        this.mode = mode
    }

    fun currentMode(): PhoneControlMode = mode

    override suspend fun captureSnapshot(): UiSnapshot = activeObserver().captureSnapshot()

    override suspend fun tap(target: ElementRef): ActionResult = activeActuator().tap(target)

    override suspend fun type(target: ElementRef, text: String): ActionResult = activeActuator().type(target, text)

    override suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult = activeActuator().scroll(target, direction)

    override suspend fun pressBack(): ActionResult = activeActuator().pressBack()

    override suspend fun pressHome(): ActionResult = activeActuator().pressHome()

    private fun activeObserver(): PhoneObserver = if (mode == PhoneControlMode.DemoPhone) demoBridge else liveObserver

    private fun activeActuator(): PhoneActuator = if (mode == PhoneControlMode.DemoPhone) demoBridge else liveActuator
}
