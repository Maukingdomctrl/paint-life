package com.mauforge.paintlife.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.NotYetBuiltState

@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    NotYetBuiltState(
        title = stringResource(R.string.title_progress),
        iteration = 7,
        summary = stringResource(R.string.coming_progress),
        modifier = modifier,
    )
}
