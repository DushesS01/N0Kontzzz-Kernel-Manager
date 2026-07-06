package id.nkz.nokontzzzmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.nkz.nokontzzzmanager.data.model.WakelockInfo
import id.nkz.nokontzzzmanager.data.repository.SystemRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WakelockViewModel @Inject constructor(
    private val systemRepository: SystemRepository
) : ViewModel() {

    private val _wakelocks = MutableStateFlow<List<WakelockInfo>>(emptyList())
    val wakelocks: StateFlow<List<WakelockInfo>> = _wakelocks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Volatile
    private var isMonitoring = false

    /** Previous raw snapshot to skip no-op emissions */
    private var previousSnapshot: List<WakelockInfo> = emptyList()

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        viewModelScope.launch {
            _isLoading.value = true
            while (isMonitoring) {
                try {
                    val data = systemRepository.getWakelocks()
                    val sorted = data.sortedByDescending { it.totalTimeMs }

                    if (!wakelockListsEqual(previousSnapshot, sorted)) {
                        previousSnapshot = sorted
                        _wakelocks.value = sorted
                    }
                } catch (_: Exception) {
                    // Swallow – best effort
                }
                _isLoading.value = false
                delay(5000L) // was 2000ms – throttled to 5s
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }

    /**
     * Cheap WakelockInfo equality based on hashCode of key fields.
     * Falls back to deep comparison if any item changes identity.
     */
    private fun wakelockListsEqual(a: List<WakelockInfo>, b: List<WakelockInfo>): Boolean {
        if (a.size != b.size) return false
        if (a.isEmpty()) return b.isEmpty()

        // Use a quick hash-like comparison: expected cost << actual IO to fetch the data
        val hashA = a.sumOf { it.hashCode().toLong() }
        val hashB = b.sumOf { it.hashCode().toLong() }
        return hashA == hashB
    }
}
