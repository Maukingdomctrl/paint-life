package com.mauforge.paintlife.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * The categories sheet body: one switch per category for its distraction flag. A change applies
 * to every day, past ones included, and insights follow it at once. Stateless.
 */
@Composable
fun CategoriesContent(
    categories: List<CategoryOption>,
    onToggle: (id: String, isDistraction: Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(modifier.testTag("categories_sheet"), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = stringResource(R.string.categories_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(text = stringResource(R.string.categories_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        categories.forEach { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .toggleable(value = category.isDistraction, role = Role.Switch, onValueChange = { onToggle(category.id, it) })
                    .testTag("distraction_${category.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(Modifier.size(14.dp).clip(PaintLifeTheme.shapes.bar).background(colors.category(category.colorKey)))
                Column(Modifier.weight(1f)) {
                    Text(text = category.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(if (category.isDistraction) R.string.categories_counts else R.string.categories_not_counted),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Switch(
                    checked = category.isDistraction,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.canvas,
                        uncheckedTrackColor = colors.sunken,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedBorderColor = colors.border,
                    ),
                )
            }
        }
        PrimaryButton(stringResource(R.string.action_done), onDone, Modifier.fillMaxWidth())
    }
}

/** "Change for this day" on a block of the approved schedule. */
@Composable
fun SchedulePromptContent(
    state: SchedulePromptUi,
    onChangeDay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    Column(modifier.testTag("schedule_prompt"), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = stringResource(R.string.schedule_prompt_title, state.categoryName, state.range), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(text = stringResource(R.string.schedule_prompt_body), style = MaterialTheme.typography.bodyMedium, color = PaintLifeTheme.colors.textSecondary)
        SecondaryButton(stringResource(R.string.action_change_this_day), onChangeDay, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
    }
}
