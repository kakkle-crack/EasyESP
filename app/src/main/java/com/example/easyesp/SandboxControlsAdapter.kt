package com.example.easyesp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Adapter for custom sandbox controls (Button, Switch, Slider, Interaction).
 * Sends formatted commands (e.g., "B,4,250") to the ESP32.
 */
class SandboxControlsAdapter(
    private var controls: List<SandboxControl>,
    private val onControlInteraction: (control: SandboxControl, action: String) -> Unit,
    private val onControlDelete: (control: SandboxControl) -> Unit
) : RecyclerView.Adapter<SandboxControlsAdapter.ControlViewHolder>() {

    open class ControlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.control_name)
    }

    open class PinControlViewHolder(itemView: View) : ControlViewHolder(itemView) {
        val pinTextView: TextView = itemView.findViewById(R.id.control_command_text)
    }

    class InteractionViewHolder(itemView: View) : ControlViewHolder(itemView) {
        val commandTextView: TextView = itemView.findViewById(R.id.control_command_text)
        val actionButton: Button = itemView.findViewById(R.id.control_action_button)
    }

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
            is InteractionViewHolder -> {
                holder.commandTextView.text = "Cmd: ${control.command}"
                holder.actionButton.setOnClickListener {
                    onControlInteraction(control, control.command ?: "")
                }
            }
            is ButtonViewHolder -> {
                holder.pinTextView.text = "Pin ${control.pin}"
                holder.actionButton.text = "Run (${control.value}ms)"
                holder.actionButton.setOnClickListener {
                    onControlInteraction(control, "B,${control.pin},${control.value}")
                }
            }
            is SwitchViewHolder -> {
                holder.pinTextView.text = "Pin ${control.pin}"
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
