package io.github.nastechresearch.nastech.workflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.nastechresearch.nastech.workflow.execution.WorkflowEngine
import io.github.nastechresearch.nastech.workflow.model.WorkflowRun
import io.github.nastechresearch.nastech.workflow.repository.WorkflowRepository
import io.github.nastechresearch.nastech.workflow.repository.WorkflowRepository.Loaded

class WorkflowsViewModel(
    private val repository: WorkflowRepository,
    private val engine: WorkflowEngine,
) : ViewModel() {

    val workflows: StateFlow<List<Loaded>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.setEnabled(id, enabled) }
    }

    fun delete(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCascading(id)
            onDone()
        }
    }

    suspend fun runNow(id: String): WorkflowEngine.FireOutcome = engine.fire(id)

    suspend fun history(id: String, limit: Int = 20): List<WorkflowRun> =
        repository.lastRuns(id, limit)

    suspend fun get(id: String): Loaded? = repository.getById(id)
}
