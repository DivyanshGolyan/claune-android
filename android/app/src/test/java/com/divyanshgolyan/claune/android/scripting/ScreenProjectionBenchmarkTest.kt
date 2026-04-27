package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.flatten
import java.io.File
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScreenProjectionBenchmarkTest {
    @Test
    fun `benchmark interaction projection from real screen snapshot`() {
        assumeTrue(
            "Set -Dclaune.projection.benchmark=true to run the projection benchmark.",
            System.getProperty("claune.projection.benchmark") == "true",
        )
        val fixture = File(
            System.getProperty("claune.projection.fixture")
                ?: "build/projection-fixtures/latest-screen-state.json",
        )
        assertTrue("Projection fixture does not exist: ${fixture.absolutePath}", fixture.exists())

        val warmupIterations = intProperty("claune.projection.warmup", default = 3).coerceAtLeast(0)
        val measuredIterations = intProperty("claune.projection.iterations", default = 20).coerceAtLeast(1)
        val state = ScriptJson.codec.decodeFromString(ScreenState.serializer(), fixture.readText())

        repeat(warmupIterations) {
            consume(state.toInteractionObservationPayload())
        }

        val samplesMs = buildList {
            repeat(measuredIterations) {
                val started = System.nanoTime()
                val payload = state.toInteractionObservationPayload()
                consume(payload)
                add((System.nanoTime() - started).toDouble() / 1_000_000.0)
            }
        }
        val lastProfiler = ProjectionProfiler()
        val lastPayload = state.toInteractionObservationPayload(lastProfiler)
        val report = ProjectionBenchmarkReport(
            fixturePath = fixture.absolutePath,
            snapshotId = state.snapshotId,
            foregroundPackage = state.foregroundPackage,
            inputNodeCount = state.root?.flatten().orEmpty().size,
            inputVisibleNodeCount = state.root?.flatten().orEmpty().count { it.visibleToUser },
            outputElementCount = lastPayload.elements.size,
            outputGroupCount = lastPayload.groups.size,
            outputActionCount = lastPayload.actions.size,
            summaryTextLength = lastPayload.summaryText.orEmpty().length,
            warmupIterations = warmupIterations,
            measuredIterations = measuredIterations,
            samplesMs = samplesMs,
            phaseTimings = lastProfiler.phases().map { phase ->
                ProjectionBenchmarkPhase(name = phase.name, durationMs = phase.durationMs)
            },
            stats = ProjectionTimingStats.from(samplesMs),
        )

        val reportFile = File(
            System.getProperty("claune.projection.report")
                ?: "build/reports/projection-benchmark.json",
        )
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(ScriptJson.codec.encodeToString(report))

        println(
            "Projection benchmark ${state.foregroundPackage} nodes=${report.inputNodeCount} " +
                "elements=${report.outputElementCount} groups=${report.outputGroupCount} actions=${report.outputActionCount} " +
                "medianMs=${report.stats.medianMs.formatMs()} p95Ms=${report.stats.p95Ms.formatMs()} " +
                "maxMs=${report.stats.maxMs.formatMs()} report=${reportFile.absolutePath}",
        )

        System.getProperty("claune.projection.maxMedianMs")?.toDoubleOrNull()?.let { maxMedianMs ->
            assertTrue(
                "Projection median ${report.stats.medianMs.formatMs()}ms exceeded ${maxMedianMs.formatMs()}ms",
                report.stats.medianMs <= maxMedianMs,
            )
        }
    }

    private fun intProperty(name: String, default: Int): Int = System.getProperty(name)?.toIntOrNull() ?: default

    private fun consume(payload: ScreenObservationPayload) {
        blackhole = payload.summaryText.orEmpty().length + payload.elements.size + payload.groups.size + payload.actions.size
    }

    private fun Double.formatMs(): String = "%.3f".format(this)

    private companion object {
        @Volatile
        private var blackhole: Int = 0
    }
}

@Serializable
private data class ProjectionBenchmarkReport(
    val fixturePath: String,
    val snapshotId: String,
    val foregroundPackage: String,
    val inputNodeCount: Int,
    val inputVisibleNodeCount: Int,
    val outputElementCount: Int,
    val outputGroupCount: Int,
    val outputActionCount: Int,
    val summaryTextLength: Int,
    val warmupIterations: Int,
    val measuredIterations: Int,
    val samplesMs: List<Double>,
    val phaseTimings: List<ProjectionBenchmarkPhase>,
    val stats: ProjectionTimingStats,
)

@Serializable
private data class ProjectionBenchmarkPhase(val name: String, val durationMs: Long)

@Serializable
private data class ProjectionTimingStats(
    val minMs: Double,
    val medianMs: Double,
    val meanMs: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val maxMs: Double,
) {
    companion object {
        fun from(samples: List<Double>): ProjectionTimingStats {
            val sorted = samples.sorted()
            return ProjectionTimingStats(
                minMs = sorted.first(),
                medianMs = sorted.percentile(0.50),
                meanMs = sorted.average(),
                p90Ms = sorted.percentile(0.90),
                p95Ms = sorted.percentile(0.95),
                maxMs = sorted.last(),
            )
        }
    }
}

private fun List<Double>.percentile(percentile: Double): Double {
    val index = ((size - 1) * percentile).roundToInt().coerceIn(0, size - 1)
    return this[index]
}
