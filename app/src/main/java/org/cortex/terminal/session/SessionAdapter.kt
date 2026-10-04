package org.cortex.terminal.session

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.cortex.terminal.R

class SessionAdapter(
    private val sessionManager: SessionManager,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.sessionItemRoot)
        val labelText: TextView = view.findViewById(R.id.sessionLabelText)
        val statusText: TextView = view.findViewById(R.id.sessionStatusText)
        val closeBtn: TextView = view.findViewById(R.id.btnSessionClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isActive = position == sessionManager.currentSessionIndex

        holder.labelText.text = "Session ${position + 1}"

        if (isActive) {
            holder.root.setBackgroundResource(R.drawable.session_item_active_bg)
            holder.statusText.text = "Active session"
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.cortex_primary))
        } else {
            holder.root.setBackgroundResource(R.drawable.session_item_inactive_bg)
            holder.statusText.text = "Background"
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.cortex_text_muted))
        }

        holder.closeBtn.visibility = View.VISIBLE
        holder.closeBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onClose(pos)
            }
        }

        holder.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onSelect(pos)
            }
        }
    }

    override fun getItemCount(): Int = sessionManager.sessions.size
}
