/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.ArrayDeque

class TextEditorUndoManager(
    private val editText: EditText,
    private val onUndoStatusChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
) : TextWatcher {

    private class EditItem(
        var start: Int,
        var beforeText: CharSequence,
        var afterText: CharSequence
    )

    private val undoStack = ArrayDeque<EditItem>()
    private val redoStack = ArrayDeque<EditItem>()

    var isUndoingOrRedoing = false
        private set

    private var tempBeforeText: CharSequence = ""
    private var tempStart = 0
    private var tempCount = 0

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        if (isUndoingOrRedoing) return
        tempStart = start
        tempCount = count
        tempBeforeText = s.subSequence(start, start + count).toString()
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (isUndoingOrRedoing) return
        val afterText = s.subSequence(start, start + count).toString()
        if (tempBeforeText == afterText) return

        val last = undoStack.peekLast()
        var merged = false
        if (last != null) {
            // Typing consecutive single characters
            if (last.beforeText.isEmpty() && tempBeforeText.isEmpty() &&
                afterText.length == 1 && !Character.isWhitespace(afterText[0]) &&
                last.start + last.afterText.length == start
            ) {
                last.afterText = last.afterText.toString() + afterText
                merged = true
            }
            // Deleting consecutive single characters with Backspace
            else if (last.afterText.isEmpty() && afterText.isEmpty() &&
                tempBeforeText.length == 1 &&
                last.start == start + 1
            ) {
                last.start = start
                last.beforeText = tempBeforeText.toString() + last.beforeText.toString()
                merged = true
            }
        }

        if (!merged) {
            undoStack.addLast(EditItem(start, tempBeforeText, afterText))
            if (undoStack.size > MAX_HISTORY) {
                undoStack.removeFirst()
            }
        }

        redoStack.clear()
        notifyStatusChanged()
    }

    override fun afterTextChanged(s: Editable) {}

    fun undo() {
        if (undoStack.isEmpty()) return
        val edit = undoStack.removeLast()
        isUndoingOrRedoing = true
        try {
            val editable = editText.text ?: return
            editable.replace(edit.start, edit.start + edit.afterText.length, edit.beforeText)
            val newCursor = (edit.start + edit.beforeText.length).coerceIn(0, editable.length)
            editText.setSelection(newCursor)
            redoStack.addLast(edit)
        } finally {
            isUndoingOrRedoing = false
        }
        notifyStatusChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val edit = redoStack.removeLast()
        isUndoingOrRedoing = true
        try {
            val editable = editText.text ?: return
            editable.replace(edit.start, edit.start + edit.beforeText.length, edit.afterText)
            val newCursor = (edit.start + edit.afterText.length).coerceIn(0, editable.length)
            editText.setSelection(newCursor)
            undoStack.addLast(edit)
        } finally {
            isUndoingOrRedoing = false
        }
        notifyStatusChanged()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyStatusChanged()
    }

    private fun notifyStatusChanged() {
        onUndoStatusChanged?.invoke(canUndo, canRedo)
    }

    companion object {
        private const val MAX_HISTORY = 150
    }
}
