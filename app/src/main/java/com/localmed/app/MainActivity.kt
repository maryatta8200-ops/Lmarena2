package com.localmed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.localmed.app.ui.LocalMedViewModel
import com.localmed.ui.LocalMedApp
import com.localmed.ui.LocalMedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: LocalMedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            LocalMedTheme {
                LocalMedApp(
                    state = state,
                    onSubmitQuestion = viewModel::submitQuestion,
                    onImportKnowledge = viewModel::importKnowledge,
                    onImportModel = viewModel::importModel,
                    onImportTrustedPublisher = viewModel::importTrustedPublisher,
                    onImportTrainingDataset = viewModel::importTrainingDataset,
                    onReviewKnowledge = viewModel::reviewKnowledge,
                    onDeleteKnowledge = viewModel::deleteKnowledge,
                    onValidateModel = viewModel::validateModel,
                    onActivateModel = viewModel::activateModel,
                    onRemovePublisher = viewModel::removeTrustedPublisher,
                    onDeleteTrainingDataset = viewModel::deleteTrainingDataset,
                    onSearchPubMed = viewModel::searchPubMed,
                    onSetWebSearchEnabled = viewModel::setWebSearchEnabled,
                    onCreateSmsDraft = viewModel::openSmsDraft,
                    onWhatsAppHandoff = viewModel::handoffWhatsApp,
                    onDismissNotice = viewModel::dismissNotice
                )
            }
        }
    }
}
