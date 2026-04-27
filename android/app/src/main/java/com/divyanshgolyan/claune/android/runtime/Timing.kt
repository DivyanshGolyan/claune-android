package com.divyanshgolyan.claune.android.runtime

fun elapsedMs(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000
