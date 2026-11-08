package com.mauforge.paintlife.quiet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.NotYetBuiltState

@Composable
fun QuietScreen(modifier: Modifier = Modifier) {
    NotYetBuiltState(
        title = stringResource(R.string.title_quiet),
        iteration = 5,
        summary = stringResource(R.string.coming_quiet),
        modifier = modifier,
    )
}
