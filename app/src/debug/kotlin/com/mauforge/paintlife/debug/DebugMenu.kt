package com.mauforge.paintlife.debug

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/** Debug builds only: a small menu in the top corner that opens the design gallery. */
@Composable
fun DebugMenu(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("debug_menu")) {
            Icon(
                painter = painterResource(R.drawable.ic_debug),
                contentDescription = stringResource(R.string.debug_menu),
                tint = PaintLifeTheme.colors.textSecondary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_design_gallery)) },
                onClick = {
                    open = false
                    context.startActivity(Intent(context, DesignGalleryActivity::class.java))
                },
            )
        }
    }
}
