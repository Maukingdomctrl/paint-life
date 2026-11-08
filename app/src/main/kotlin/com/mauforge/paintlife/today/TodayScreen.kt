package com.mauforge.paintlife.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.NotYetBuiltState

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    NotYetBuiltState(
        title = stringResource(R.string.title_today),
        iteration = 3,
        summary = stringResource(R.string.coming_today),
        modifier = modifier,
    )
}
