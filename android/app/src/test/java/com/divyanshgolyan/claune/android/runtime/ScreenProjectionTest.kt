package com.divyanshgolyan.claune.android.runtime

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenProjectionTest {
    @Test
    fun `compact screen text is salience ranked`() {
        val passiveText = node(
            path = listOf(0),
            elementId = "passive",
            ref = "passive",
            label = "Terms and conditions",
            clickable = false,
            focusable = false,
            bounds = listOf(0, 0, 1080, 200),
        )
        val primaryAction = node(
            path = listOf(1),
            elementId = "primary",
            ref = "primary",
            label = "Confirm booking",
            className = "android.widget.Button",
            clickable = true,
            focusable = true,
            bounds = listOf(0, 1800, 1080, 1960),
        )
        val state = screenState(children = listOf(passiveText, primaryAction))

        val text = state.toCanonicalScreenText()

        assertTrue(text.startsWith("screen-v1-salience"))
        assertTrue(text.contains("priority:"))
        assertTrue(text.indexOf("Confirm booking") < text.indexOf("Terms and conditions"))
    }

    @Test
    fun `compact screen text omits low priority overflow before truncation`() {
        val primaryAction = node(
            path = listOf(0),
            elementId = "primary",
            ref = "primary",
            label = "Primary ride option",
            className = "android.widget.Button",
            clickable = true,
            focusable = true,
        )
        val passiveNodes = (1..80).map { index ->
            node(
                path = listOf(index),
                elementId = "passive-$index",
                ref = "passive-$index",
                label = "Passive visible label $index",
                clickable = false,
                focusable = false,
            )
        }
        val state = screenState(children = listOf(primaryAction) + passiveNodes)

        val text = state.toCanonicalScreenText()

        assertTrue(text.contains("Primary ride option"))
        assertFalse(text.contains("Passive visible label 80"))
    }

    @Test
    fun `compact screen text includes descendant details for salient actions`() {
        val price = node(
            path = listOf(0, 0),
            elementId = "price",
            ref = "price",
            label = "Auto, price range 246 to 256, capacity 3",
            clickable = false,
            focusable = false,
        )
        val rideCard = node(
            path = listOf(0),
            elementId = "ride-card",
            ref = "ride-card",
            label = "Choose ride card Auto button",
            className = "android.widget.Button",
            clickable = true,
            focusable = true,
            children = listOf(price),
        )
        val state = screenState(children = listOf(rideCard))

        val text = state.toCanonicalScreenText()

        assertTrue(text.contains("Choose ride card Auto button"))
        assertTrue(text.contains("details=\"Auto, price range 246 to 256, capacity 3\""))
    }

    private fun screenState(children: List<ScreenNode>): ScreenState {
        val root = node(
            path = emptyList(),
            elementId = "root",
            ref = "root",
            label = "Root",
            role = "root",
            clickable = false,
            focusable = false,
            bounds = listOf(0, 0, 1080, 2400),
            children = children,
        )
        return ScreenState(
            snapshotId = "snapshot-1",
            capturedAt = Instant.parse("2026-04-26T00:00:00Z").toString(),
            foregroundPackage = "com.example",
            root = root,
        )
    }

    private fun node(
        path: List<Int>,
        elementId: String,
        ref: String,
        label: String,
        role: String = "control",
        className: String = "android.view.View",
        clickable: Boolean,
        focusable: Boolean,
        bounds: List<Int> = listOf(0, 0, 100, 100),
        children: List<ScreenNode> = emptyList(),
    ): ScreenNode = ScreenNode(
        path = path,
        ref = ref,
        elementId = elementId,
        role = role,
        label = label,
        className = className,
        visibleToUser = true,
        clickable = clickable,
        focusable = focusable,
        editable = false,
        focused = false,
        bounds = bounds,
        children = children,
    )
}
