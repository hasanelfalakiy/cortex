package org.cortex.terminal.view

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class TerminalSearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onQueryChanged: ((String) -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val input: EditText
    private val countText: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#11111b"))
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        visibility = GONE

        input = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(Color.parseColor("#1e1e2e"))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6c7086"))
            hint = "Search terminal…"
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(pad, pad, pad, pad)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    onQueryChanged?.invoke(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onNext?.invoke()
                    true
                } else false
            }
        }
        addView(input)

        countText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (4 * resources.displayMetrics.density).toInt()
            }
            setTextColor(Color.parseColor("#a6adc8"))
            textSize = 12f
            text = "0/0"
        }
        addView(countText)

        val btnPrev = ImageButton(context).apply {
            setImageResource(android.R.drawable.arrow_up_float)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Previous match"
            setColorFilter(Color.WHITE)
            setOnClickListener { onPrev?.invoke() }
        }
        addView(btnPrev)

        val btnNext = ImageButton(context).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Next match"
            setColorFilter(Color.WHITE)
            setOnClickListener { onNext?.invoke() }
        }
        addView(btnNext)

        val btnClose = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close search"
            setColorFilter(Color.WHITE)
            setOnClickListener { hide() }
        }
        addView(btnClose)
    }

    fun show() {
        visibility = VISIBLE
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        visibility = GONE
        input.setText("")
        try { onClose?.invoke() } catch (_: Exception) {}
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        try { imm?.hideSoftInputFromWindow(windowToken, 0) } catch (_: Exception) {}
    }

    fun isShowing(): Boolean = visibility == VISIBLE

    fun updateCount(index: Int, total: Int) {
        countText.text = if (total <= 0) "0/0" else "${index + 1}/$total"
    }
}
