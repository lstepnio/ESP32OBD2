package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.lstepnio.egauge.IdlePresetId
import com.lstepnio.egauge.IdleTargetDraft

/** Edits only the existing phone draft. It grants no controller capability. */
@Composable
fun IdleTargetEditor(id: IdlePresetId, text: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        text, onChange,
        label = { Text(IdleTargetDraft.label(id)) },
        supportingText = {
            Text("400 to 3,000 RPM · Saved on phone only. Leave blank for the car's fixed setting.")
        },
        isError = !IdleTargetDraft.valid(text), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled, modifier = Modifier.fillMaxWidth()
    )
    Text(
        if (id == IdlePresetId.AirUp) {
            "Target RPM and pedal cancellation need controller verification before use."
        } else {
            "Minimum idle with normal throttle above it needs controller verification before use."
        }
    )
}
