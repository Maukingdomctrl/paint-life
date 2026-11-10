package com.mauforge.paintlife.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.core.backup.BackupSection
import com.mauforge.paintlife.core.designsystem.component.LoadingState

@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
    backup: @Composable () -> Unit = { BackupSection() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state
    if (content == null) {
        LoadingState(modifier)
    } else {
        ProgressContent(content, onPeriodChange = viewModel::setPeriod, modifier = modifier, footer = backup)
    }
}
