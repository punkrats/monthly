package app.monthly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monthly.R

@Composable
fun rememberFormat(): Format {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  return remember(configuration) { Format(context) }
}

/** A tappable settings-style row: icon, label and current value. */
@Composable
fun PickerRow(
  icon: ImageVector,
  label: String,
  value: String? = null,
  onClick: () -> Unit
) {
  ListItem(
    onClick = onClick,
    leadingContent = { Icon(icon, contentDescription = null) },
    supportingContent = value?.let { { Text(it) } },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
  ) {
    Text(label)
  }
}

@Composable
fun Stepper(
  value: Int,
  range: IntRange,
  label: String,
  onChange: (Int) -> Unit
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = { onChange(value - 1) },
      enabled = value > range.first
    ) {
      Icon(Icons.Filled.Remove, stringResource(R.string.less))
    }
    Text(label)
    IconButton(
      onClick = { onChange(value + 1) },
      enabled = value < range.last
    ) {
      Icon(Icons.Filled.Add, stringResource(R.string.more))
    }
  }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(color)
  )
}
