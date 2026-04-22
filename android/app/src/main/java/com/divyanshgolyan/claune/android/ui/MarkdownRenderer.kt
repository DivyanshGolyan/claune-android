package com.divyanshgolyan.claune.android.ui

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

internal object MarkdownRenderer {
    fun create(context: Context): Markwon = Markwon.builder(context)
        .usePlugin(TablePlugin.create(context))
        .build()

    fun render(markwon: Markwon, textView: TextView, markdown: String) {
        textView.movementMethod = LinkMovementMethod.getInstance()
        markwon.setMarkdown(textView, markdown)
    }
}
