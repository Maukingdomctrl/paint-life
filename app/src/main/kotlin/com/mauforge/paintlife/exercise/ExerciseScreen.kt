package com.mauforge.paintlife.exercise

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.NotYetBuiltState

@Composable
fun ExerciseScreen(modifier: Modifier = Modifier) {
    NotYetBuiltState(
        title = stringResource(R.string.title_exercise),
        iteration = 4,
        summary = stringResource(R.string.coming_exercise),
        modifier = modifier,
    )
}
