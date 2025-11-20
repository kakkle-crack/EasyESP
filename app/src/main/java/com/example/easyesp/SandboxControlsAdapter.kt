package com.example.easyesp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class SandboxControlsAdapter(
    private var controls: List<SandboxControl>,
    private val onControlInteraction: (control: SandboxControl, action: String) -> Unit,
    private val onControlDelete: (control: SandboxControl) -> Unit
) : RecyclerView.Adapter<SandboxControlsAdapter.ControlViewHolder>() {

    // Base ViewHolder is now simpler
    open class ControlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.control_name)
    }

    // ViewHolder for pin-based controls
    open class PinControlViewHolder(itemView: View) : ControlViewHolder(itemView) {
        val pinTextView: TextView = itemView.findViewById(R.id.control_command_text)
    }

    // --- ADD A NEW VIEWHOLDER FOR INTERACTION ---
    class InteractionViewHolder(itemView: View) : ControlViewHolder(itemView) {
        val commandTextView: TextView = itemView.findViewById(R.id.control_command_text)
        val actionButton: Button = itemView.findViewById(R.id.control_action_button)
    }

    // Update existing ViewHolders to inherit from PinControlViewHolder
    class ButtonViewHolder(itemView: View) : PinControlViewHolder(itemView) {
        val actionButton: Button = itemView.findViewById(R.id.control_action_button)
    }

    class SwitchViewHolder(itemView: View) : PinControlViewHolder(itemView) {
        val actionSwitch: SwitchMaterial = itemView.findViewById(R.id.control_action_switch)
    }

    class SliderViewHolder(itemView: View) : PinControlViewHolder(itemView) {
        val actionSlider: SeekBar = itemView.findViewById(R.id.control_action_slider)
        val sliderValueTextView: TextView = itemView.findViewById(R.id.control_slider_value)
    }

    override fun getItemViewType(position: Int): Int {
        return when (controls[position].type) {
            ControlType.BUTTON -> R.layout.item_control_button
            ControlType.SWITCH -> R.layout.item_control_switch
            ControlType.SLIDER -> R.layout.item_control_slider
            ControlType.INTERACTION -> R.layout.item_control_interaction
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ControlViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.item_control_button -> ButtonViewHolder(view)
            R.layout.item_control_switch -> SwitchViewHolder(view)
            R.layout.item_control_slider -> SliderViewHolder(view)
            // --- ADD THE NEW TYPE ---
            R.layout.item_control_interaction -> InteractionViewHolder(view)
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: ControlViewHolder, position: Int) {
        val control = controls[position]
        holder.nameTextView.text = control.name

        holder.itemView.setOnLongClickListener {
            onControlDelete(control)
            true
        }

        when (holder) {
            // --- HANDLE THE VIEWHOLDER ---
            is InteractionViewHolder -> {
                holder.commandTextView.text = "Cmd: ${control.command}"
                holder.actionButton.setOnClickListener {
                    // For Interaction, the action string IS the command itself.
                    onControlInteraction(control, control.command ?: "")
                }
            }

            is ButtonViewHolder -> {
                holder.pinTextView.text = "Pin ${control.pin}"
                // Make the button text more descriptive
                holder.actionButton.text = "Run (${control.value}ms)"
                holder.actionButton.setOnClickListener {
                    onControlInteraction(control, "B,${control.pin},${control.value}")
                }
            }
            is SwitchViewHolder -> {
                holder.pinTextView.text = "Pin ${control.pin}"
                // Listener needs to be set before isChecked to avoid premature trigger
                holder.actionSwitch.setOnCheckedChangeListener(null)
                holder.actionSwitch.isChecked = control.value == 1
                holder.actionSwitch.setOnCheckedChangeListener { _, isChecked ->
                    control.value = if (isChecked) 1 else 0
                    onControlInteraction(control, "S,${control.pin},${control.value}")
                }
            }
            is SliderViewHolder -> {
                holder.pinTextView.text = "Pin ${control.pin}"
                holder.actionSlider.max = 255
                // Safe call for nullable value, default to 0
                holder.actionSlider.progress = control.value ?: 0
                holder.sliderValueTextView.text = control.value.toString()

                holder.actionSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            holder.sliderValueTextView.text = progress.toString()
                            control.value = progress
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // Safe calls for nullable pin and value
                        onControlInteraction(control, "V,${control.pin ?: 0},${control.value ?: 0}")
                    }
                })
            }
        }
    }

    override fun getItemCount(): Int = controls.size

    fun updateControls(newControls: List<SandboxControl>) {
        this.controls = newControls
        notifyDataSetChanged()
    }
}