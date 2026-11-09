package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * Bottom sheet frame: surface colour, screen gutter, and a scrolling body so large text never
 * pushes actions off screen. [modifier] applies to the body column (tags, semantics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintLifeSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaintLifeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaintLifeTheme.spacing.screenGutter)
                .padding(bottom = PaintLifeTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.lg),
            content = content,
        )
    }
}

/**
 * Confirmation before a bigger destructive action (reset, delete from history) or a choice that
 * throws something away. The confirming button is a [DestructiveButton]; the other one only
 * closes the dialog. Both labels say what they do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier.semantics { paneTitle = title }) {
        Surface(shape = PaintLifeTheme.shapes.card, color = colors.raised, contentColor = colors.text) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(text = body, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                DestructiveButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = spacing.sm))
                Row(Modifier.fillMaxWidth()) {
                    QuietButton(text = dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Text field colours used on sheets: sunken fill, green focus. */
@Composable
fun paintLifeFieldColors(): TextFieldColors {
    val colors = PaintLifeTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent,
        focusedContainerColor = colors.sunken,
        unfocusedContainerColor = colors.sunken,
        focusedSupportingTextColor = colors.textSecondary,
        unfocusedSupportingTextColor = colors.textSecondary,
        errorSupportingTextColor = colors.danger,
        errorBorderColor = colors.danger,
        errorLabelColor = colors.danger,
    )
}
