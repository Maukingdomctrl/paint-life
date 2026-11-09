package com.mauforge.paintlife.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release builds have no debug menu and no design gallery. The debug version lives in src/debug. */
@Composable
fun DebugMenu(modifier: Modifier = Modifier) = Unit
