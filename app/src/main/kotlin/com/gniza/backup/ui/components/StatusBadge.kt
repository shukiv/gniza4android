package com.gniza.backup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gniza.backup.domain.model.BackupStatus
import com.gniza.backup.ui.theme.GnizaAmber
import com.gniza.backup.ui.theme.GnizaError
import com.gniza.backup.ui.theme.GnizaSuccess

@Composable
fun StatusBadge(
    status: BackupStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (status) {
        BackupStatus.SUCCESS -> GnizaSuccess
        BackupStatus.FAILED -> GnizaError
        BackupStatus.RUNNING -> GnizaAmber
        BackupStatus.CANCELLED -> Color.Gray
    }

    val label = when (status) {
        BackupStatus.SUCCESS -> "Success"
        BackupStatus.FAILED -> "Failed"
        BackupStatus.RUNNING -> "Running"
        BackupStatus.CANCELLED -> "Cancelled"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
