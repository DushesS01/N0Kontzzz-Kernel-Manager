package id.nkz.nokontzzzmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.nkz.nokontzzzmanager.data.repository.RootRepository
import id.nkz.nokontzzzmanager.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@HiltViewModel
class KernelLogViewModel @Inject constructor(
    private val rootRepository: RootRepository,
    private val themeManager: ThemeManager
) : ViewModel() {

    val isAmoledMode = themeManager.isAmoledMode
    
    private val _exportTrigger = Channel<Unit>(Channel.BUFFERED)
    val exportTrigger = _exportTrigger.receiveAsFlow()

    private val _logContent = MutableStateFlow<List<String>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _isMenuExpanded = MutableStateFlow(false)
    val isMenuExpanded: StateFlow<Boolean> = _isMenuExpanded.asStateFlow()

    // Filtered content based on search query with debounce
    val logContent: StateFlow<List<String>> = combine(
        _logContent,
        _searchQuery.debounce(300)
    ) { logs, query ->
        if (query.isBlank()) logs else logs.filter { it.contains(query, ignoreCase = true) }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var monitoringJob: Job? = null
    private var isFirstLoad = true
    private var lastLogRaw: String? = null

    /** Interval in ms: 2000 when unpaused, 30000 when paused */
    private val _pollInterval = MutableStateFlow(2000L)

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        // Start paused so a 30 s sleep doesn't fire immediately
        if (!isFirstLoad) {
            // If resuming, reset to normal interval
        }

        monitoringJob = viewModelScope.launch {
            while (true) {
                val interval = if (_isPaused.value) 30000L else 2000L

                if (!_isPaused.value) {
                    loadLogsInternal(quiet = !isFirstLoad)
                    isFirstLoad = false
                }
                // Skip the log fetch while paused, but still delay to avoid spinning
                delay(interval)
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    fun togglePause() {
        val wasPaused = _isPaused.value
        _isPaused.value = !wasPaused

        // If un-pausing, reset to fast poll immediately
        if (wasPaused) {
            viewModelScope.launch {
                loadLogsInternal(quiet = false)
                isFirstLoad = false
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSearchVisible(visible: Boolean) {
        _isSearchVisible.value = visible
        if (!visible) _searchQuery.value = ""
    }

    fun setMenuExpanded(expanded: Boolean) {
        _isMenuExpanded.value = expanded
    }
    
    fun triggerExport() {
        viewModelScope.launch {
            _exportTrigger.send(Unit)
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            loadLogsInternal(quiet = false)
        }
    }
    
    // Helper to get raw logs for export
    suspend fun getRawLogs(): String {
        return _logContent.value.joinToString("\n")
    }

    private suspend fun loadLogsInternal(quiet: Boolean) {
        if (!quiet) {
            _isLoading.value = true
        }
        _error.value = null
        try {
            // Execute dmesg in background
            val result = withContext(Dispatchers.IO) {
                try {
                     rootRepository.run("dmesg")
                } catch (e: Exception) {
                    throw e
                }
            }
            
            // Optimization: Compare raw strings to avoid splitting and list comparison if unchanged
            if (result != lastLogRaw) {
                lastLogRaw = result
                if (result.isNotBlank()) {
                    val lines = result.lines()
                    // Limit to latest 2000 lines to preserve RAM
                    _logContent.value = if (lines.size > 2000) lines.takeLast(2000) else lines
                } else {
                    _logContent.value = emptyList()
                }
            }
        } catch (e: Exception) {
            if (!quiet) {
                _error.value = e.message
                _logContent.value = emptyList()
            }
        } finally {
            if (!quiet) {
                _isLoading.value = false
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    rootRepository.run("dmesg -C")
                }
                lastLogRaw = null // Reset cache
                loadLogsInternal(quiet = false)
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
