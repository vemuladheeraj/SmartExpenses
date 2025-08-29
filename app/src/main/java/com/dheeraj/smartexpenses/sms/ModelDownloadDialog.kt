package com.dheeraj.smartexpenses.sms

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ModelDownloadDialog(
    onDismiss: () -> Unit,
    viewModel: ModelDownloadViewModel = viewModel()
) {
    // Removed: LLM download UI. Immediately dismiss.
    onDismiss()
}
