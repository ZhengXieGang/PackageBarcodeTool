package com.example.expresscode.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 预分配常用圆角形状，避免每次 recomposition 创建新对象
private val ItemShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BarcodeListScreen(
    barcodeList: List<String>,
    onAddBarcode: (String) -> Boolean,
    onRemoveBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = LocalHapticFeedback.current
    var inputText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // 手动输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("手动输入条码") },
                singleLine = true,
                shape = ItemShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (inputText.isNotBlank()) {
                            val added = onAddBarcode(inputText.trim())
                            if (added) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            inputText = ""
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val added = onAddBarcode(inputText.trim())
                        if (added) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        inputText = ""
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }

        // 条码总数
        Text(
            text = "共 ${barcodeList.size} 个条码",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // 列表
        if (barcodeList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无条码",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请扫描条码或手动输入添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 4.dp, 
                    start = 16.dp, 
                    end = 16.dp, 
                    bottom = contentPadding.calculateBottomPadding() + 4.dp
                ), // Combine internal padding with external content padding (FAB space)
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 使列表填满除了顶部输入框以外的全部高度，无需使用耗时的 fillMaxSize 强行夺取。
            ) {
                itemsIndexed(
                    items = barcodeList,
                    key = { _, item -> item }
                ) { index, barcode ->
                    // 缓存 Lambda，避免每次重画生成新的对象导致底下组件因入参不同被强制刷新
                    val handleLongClick = remember(barcode) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            deleteTarget = barcode
                        }
                    }
                    BarcodeListItem(
                        barcode = barcode,
                        index = index,
                        onLongClick = handleLongClick
                    )
                }
            }
        }

        // 删除确认弹窗
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("确认删除") },
                text = { Text("是否删除条码 \"$target\"？") },
                confirmButton = {
                    TextButton(onClick = {
                        onRemoveBarcode(target)
                        deleteTarget = null
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("取消")
                    }
                },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarcodeListItem(
    barcode: String,
    index: Int,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        // Card 自己已通过 shape 裁剪，不需要冗余的 .clip()——双重裁剪层每滚动一帧都会多做一次路径计算
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = ItemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = barcode,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
