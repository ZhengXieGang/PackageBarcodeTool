package com.example.expresscode.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.expresscode.viewmodel.BarcodeViewModel

@Composable
fun SettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    currentMode: BarcodeViewModel.CarouselMode,
    onModeChange: (BarcodeViewModel.CarouselMode) -> Unit,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    showText: Boolean,
    onShowTextChange: (Boolean) -> Unit,
    autoJump: Boolean,
    onAutoJumpChange: (Boolean) -> Unit,
    urlScheme: String,
    onUrlSchemeChange: (String) -> Unit,
    autoStartDisabled: Boolean,
    onAutoStartDisabledChange: (Boolean) -> Unit,
    onAdjustWindowClick: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "轮播设置") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 轮播模式
                    Text(
                        text = "轮播方式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(Modifier.selectableGroup()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (currentMode == BarcodeViewModel.CarouselMode.InApp),
                                    onClick = { onModeChange(BarcodeViewModel.CarouselMode.InApp) },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentMode == BarcodeViewModel.CarouselMode.InApp),
                                onClick = null // null recommended for accessibility with selectable
                            )
                            Text(
                                text = "应用内轮播",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                        
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (currentMode == BarcodeViewModel.CarouselMode.Suspended),
                                    onClick = { onModeChange(BarcodeViewModel.CarouselMode.Suspended) },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentMode == BarcodeViewModel.CarouselMode.Suspended),
                                onClick = null
                            )
                            Text(
                                text = "悬浮窗轮播",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 轮播速度
                    Text(
                        text = "轮播速度: ${"%.1f".format(currentSpeed / 1000f)}秒",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = currentSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 500f..3000f,
                        steps = 24,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 显示条码内容开关
                    if (currentMode == BarcodeViewModel.CarouselMode.Suspended) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "显示条码内容",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showText,
                                onCheckedChange = onShowTextChange
                            )
                        }
                    }

                    // 轮播不自动开始
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "轮播不自动开始",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoStartDisabled,
                            onCheckedChange = onAutoStartDisabledChange
                        )
                    }

                    // 悬浮窗跳转配置
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "悬浮窗轮播自动跳转",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(
                            checked = autoJump,
                            onCheckedChange = onAutoJumpChange
                        )
                    }

                    if (autoJump) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = urlScheme,
                            onValueChange = onUrlSchemeChange,
                            label = { Text("跳转 URL Scheme") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // 悬浮窗调整
                    if (currentMode == BarcodeViewModel.CarouselMode.Suspended) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAdjustWindowClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("调整悬浮窗位置与大小")
                        }
                        Text(
                            text = "点击后将显示空悬浮窗，拖动右下角调整大小，点击保存即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            }
        )
    }
}
