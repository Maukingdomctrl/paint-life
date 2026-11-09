package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** The UI locale, read observably so dates and weekday names update if the locale changes. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
