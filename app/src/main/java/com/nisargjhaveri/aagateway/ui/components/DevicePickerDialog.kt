package com.nisargjhaveri.aagateway.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nisargjhaveri.aagateway.BluetoothHandler

@Composable
fun DevicePickerDialog(
    title: String,
    devices: List<BluetoothHandler.BluetoothDeviceInfo>,
    onDismiss: () -> Unit,
    onSelect: (BluetoothHandler.BluetoothDeviceInfo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier =
                    Modifier.heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                if (devices.isEmpty()) {
                    Text(
                        text = "No paired devices found. Pair the phones first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    devices.forEach { device ->
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { onSelect(device) }
                                    .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = device.name ?: "Unnamed device",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = device.address,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
