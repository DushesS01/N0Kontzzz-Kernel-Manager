package id.nkz.nokontzzzmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nkz.nokontzzzmanager.R
import id.nkz.nokontzzzmanager.data.model.StorageInfo
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

@Composable
private fun StorageProgressSection(
    storageInfo: StorageInfo
) {
    val usedPercentage = if (storageInfo.totalSpace > 0) {
        ((storageInfo.usedSpace.toDouble() / storageInfo.totalSpace.toDouble()) * 100).roundToInt()
    } else 0

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = stringResource(id = R.string.internal_storage),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(id = R.string.internal_storage),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(id = R.string.storage_usage_template, formatStorageSize(storageInfo.usedSpace), formatStorageSize(storageInfo.totalSpace)),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.secondary
            )
        }

        val progressColor = when {
            usedPercentage < 70 -> MaterialTheme.colorScheme.primary
            usedPercentage < 85 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
        LinearProgressIndicator(
            progress = { usedPercentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        // Storage Details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(id = R.string.used_template, formatStorageSize(storageInfo.usedSpace)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(id = R.string.free_template, formatStorageSize(storageInfo.totalSpace - storageInfo.usedSpace)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageStatsSection(
    storageInfo: StorageInfo
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.system_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Storage Stats Row 1 - Used and Free
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Used Storage
                SystemStatItem(
                    icon = Icons.Default.Storage,
                    label = stringResource(id = R.string.used_storage),
                    value = formatStorageSize(storageInfo.usedSpace),
                    modifier = Modifier.weight(1f)
                )

                // Free Storage
                SystemStatItem(
                    icon = Icons.Default.Storage,
                    label = stringResource(id = R.string.free_storage),
                    value = formatStorageSize(storageInfo.totalSpace - storageInfo.usedSpace),
                    modifier = Modifier.weight(1f)
                )
            }

            // Storage Stats Row 2 - Total and Usage Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Storage
                SystemStatItem(
                    icon = Icons.Default.Storage,
                    label = stringResource(id = R.string.total_storage),
                    value = formatStorageSize(storageInfo.totalSpace),
                    modifier = Modifier.weight(1f)
                )

                // Usage Percentage
                SystemStatItem(
                    icon = Icons.Default.Analytics,
                    label = stringResource(id = R.string.usage_percentage_label),
                    value = stringResource(id = R.string.usage_percentage, ((storageInfo.usedSpace.toDouble() / storageInfo.totalSpace.toDouble()) * 100).roundToInt()),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
