package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.NotYetBuiltState

@Composable
fun MyTimeScreen(modifier: Modifier = Modifier) {
    NotYetBuiltState(
        title = stringResource(R.string.title_my_time),
        iteration = 6,
        summary = stringResource(R.string.coming_my_time),
        modifier = modifier,
    )
}
