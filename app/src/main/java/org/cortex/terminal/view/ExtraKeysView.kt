package org.cortex.terminal.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import org.cortex.terminal.R

class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onSearchToggle: (() -> Unit)? = null

    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeConsumed = false

    // Deferred key dispatch: a press only fires its key after a tiny delay so a
    // horizontal swipe across the toolbar can cancel it instead of typing garbage.
    private var pendingKeyAction: Runnable? = null
    private var keyDownX = 0f
    private var keyDownY = 0f

    var terminalView: TerminalView? = null
        set(value) {
            field = value
            value?.onModifiersChanged = {
                post { updateModifierStyles() }
            }
            updateModifierStyles()
        }

    var onMenuClick: (() -> Unit)? = null

    private var ctrlButton: Button? = null
    private var altButton: Button? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#000000"))
        buildLayout()
    }

    private fun buildLayout() {
        val row1Keys = listOf(
            "ESC" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_ESCAPE) },
            "☰" to { onMenuClick?.invoke() },
            "FIND" to { onSearchToggle?.invoke() },
            "HOME" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_MOVE_HOME) },
            "↑" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_DPAD_UP) },
            "END" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_MOVE_END) },
            "PGUP" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_PAGE_UP) }
        )

        val row2Keys = listOf(
            "⇆" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_TAB) },
            "CTRL" to { toggleCtrl() },
            "ALT" to { toggleAlt() },
            "←" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_DPAD_LEFT) },
            "↓" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_DPAD_DOWN) },
            "→" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_DPAD_RIGHT) },
            "PGDN" to { terminalView?.sendKeySequence(KeyEvent.KEYCODE_PAGE_DOWN) }
        )

        addView(createRow(row1Keys))
        addView(createRow(row2Keys))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createRow(keys: List<Pair<String, () -> Any?>>): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (38 * resources.displayMetrics.density).toInt()
            )
        }

        val marginPx = (2 * resources.displayMetrics.density).toInt()

        for ((label, action) in keys) {
            val btn = Button(context).apply {
                text = label
                textSize = if (label.length > 3) 10f else 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#ffffff"))
                setBackgroundResource(R.drawable.key_button_bg)
                isAllCaps = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                stateListAnimator = null

                val params = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params

                val moveSlopPx = 22 * resources.displayMetrics.density
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            keyDownX = event.x
                            keyDownY = event.y
                            cancelPendingKey(v)
                            val runnable = Runnable {
                                pendingKeyAction = null
                                try { action() } catch (_: Exception) {}
                            }
                            pendingKeyAction = runnable
                            pendingKeyView = v
                            v.postDelayed(runnable, 55L)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val moved = kotlin.math.abs(event.x - keyDownX) > moveSlopPx ||
                                        kotlin.math.abs(event.y - keyDownY) > moveSlopPx
                            if (moved) {
                                cancelPendingKey(v)
                                v.isPressed = false
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            v.isPressed = false
                            // Fire immediately if the tap ended before the deferral elapsed
                            pendingKeyAction?.let { r ->
                                cancelPendingKey(v)
                                try { r.run() } catch (_: Exception) {}
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            v.isPressed = false
                            cancelPendingKey(v)
                            true
                        }
                        else -> false
                    }
                }
            }

            if (label == "CTRL") ctrlButton = btn
            if (label == "ALT") altButton = btn

            rowLayout.addView(btn)
        }

        return rowLayout
    }

    private var pendingKeyView: View? = null

    private fun cancelPendingKey(v: View? = null) {
        val target = v ?: pendingKeyView
        pendingKeyAction?.let { r ->
            try { target?.removeCallbacks(r) } catch (_: Exception) {}
        }
        pendingKeyAction = null
        pendingKeyView = null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = ev.x
                swipeDownY = ev.y
                swipeConsumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeConsumed) {
                    val dx = ev.x - swipeDownX
                    val dy = kotlin.math.abs(ev.y - swipeDownY)
                    val density = resources.displayMetrics.density
                    // Right-to-left swipe across the toolbar opens terminal search
                    if (dx < -45 * density && kotlin.math.abs(dx) > dy * 1.3f) {
                        swipeConsumed = true
                        cancelPendingKey()
                        try { onSearchToggle?.invoke() } catch (_: Exception) {}
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun toggleCtrl() {
        val view = terminalView ?: return
        view.isCtrlPressed = !view.isCtrlPressed
        updateModifierStyles()
    }

    private fun toggleAlt() {
        val view = terminalView ?: return
        view.isAltPressed = !view.isAltPressed
        updateModifierStyles()
    }

    fun updateModifierStyles() {
        val view = terminalView ?: return

        ctrlButton?.let { btn ->
            if (view.isCtrlPressed) {
                btn.setBackgroundResource(R.drawable.key_button_active)
                btn.setTextColor(Color.parseColor("#181825"))
            } else {
                btn.setBackgroundResource(R.drawable.key_button_bg)
                btn.setTextColor(Color.parseColor("#ffffff"))
            }
        }

        altButton?.let { btn ->
            if (view.isAltPressed) {
                btn.setBackgroundResource(R.drawable.key_button_active)
                btn.setTextColor(Color.parseColor("#181825"))
            } else {
                btn.setBackgroundResource(R.drawable.key_button_bg)
                btn.setTextColor(Color.parseColor("#ffffff"))
            }
        }
    }
}
