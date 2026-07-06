package id.nkz.nokontzzzmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.nkz.nokontzzzmanager.data.repository.RootRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lightweight per-process data parsed from `ps` only */
data class ProcessInfo(
    val pid: String,
    val user: String,
    val cpu: Float,
    val ram: Float, // MB
    val name: String,
    val isUserApp: Boolean
)

enum class ProcessSort {
    CPU, RAM
}

enum class ProcessFilter {
    ALL, USER_APPS, SYSTEM_ROOT
}

@HiltViewModel
class ProcessMonitorViewModel @Inject constructor(
    private val rootRepository: RootRepository
) : ViewModel() {

    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _sampleRate = MutableStateFlow(2000L)
    val sampleRate: StateFlow<Long> = _sampleRate.asStateFlow()

    private val _maxProcesses = MutableStateFlow(20)
    val maxProcesses: StateFlow<Int> = _maxProcesses.asStateFlow()

    private val _sortOption = MutableStateFlow(ProcessSort.CPU)
    val sortOption: StateFlow<ProcessSort> = _sortOption.asStateFlow()

    private val _filterOption = MutableStateFlow(ProcessFilter.ALL)
    val filterOption: StateFlow<ProcessFilter> = _filterOption.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var monitorJob: Job? = null

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        if (_processes.value.isEmpty()) {
            _isLoading.value = true
        }

        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            var lastCpuStat: CpuStat? = null

            while (isActive) {
                try {
                    // ── 1. Single, lightweight pass: ps + /proc/stat + targeted /proc/{pid}/stat ──
                    // Use `ps` as the primary data source; supplement with delta-based
                    // CPU for the top-N by RSS/Memory processes.
                    val psOutput = rootRepository.run("ps -A -o PID,USER,%CPU,RSS,ARGS")
                    val psEntries = parsePsOutput(psOutput)

                    if (psEntries.isEmpty()) {
                        delay(500)
                        continue
                    }

                    // ── 2. Delta CPU for the most memory-heavy candidates ──
                    val topCandidates = psEntries
                        .sortedByDescending { it.rssKb }
                        .take(50)
                        .map { it.pid }

                    val currentCpuStat = readTargetedCpuStats(topCandidates)

                    val cpuMap = if (lastCpuStat != null) {
                        computeDeltaCpu(lastCpuStat, currentCpuStat)
                    } else {
                        emptyMap()
                    }
                    lastCpuStat = currentCpuStat

                    // ── 3. Build ProcessInfo list, preferring delta CPU, falling back to `ps` %CPU ──
                    val newProcessList = psEntries.mapNotNull { entry ->
                        val cpuUsage: Float = when {
                            entry.pid in cpuMap -> cpuMap[entry.pid]!!
                            entry.psCpu > 0f -> entry.psCpu
                            else -> 0f
                        }

                        val isUserApp = entry.user.startsWith("u0_") ||
                                entry.user.startsWith("app_") ||
                                entry.name.contains("com.") ||
                                entry.name.contains("id.")

                        ProcessInfo(
                            pid = entry.pid,
                            user = entry.user,
                            cpu = cpuUsage,
                            ram = entry.rssKb / 1024f,
                            name = entry.name,
                            isUserApp = isUserApp
                        )
                    }

                    // ── 4. Sort, filter, and cap ──
                    val sorted = when (_sortOption.value) {
                        ProcessSort.CPU -> newProcessList
                            .sortedWith(
                                compareByDescending<ProcessInfo> { it.cpu }
                                    .thenByDescending { it.ram }
                            )
                        ProcessSort.RAM -> newProcessList
                            .sortedWith(
                                compareByDescending<ProcessInfo> { it.ram }
                                    .thenByDescending { it.cpu }
                            )
                    }

                    val filtered = when (_filterOption.value) {
                        ProcessFilter.ALL -> sorted
                        ProcessFilter.USER_APPS -> sorted.filter { it.isUserApp }
                        ProcessFilter.SYSTEM_ROOT -> sorted.filter { !it.isUserApp }
                    }

                    _processes.value = filtered.take(_maxProcesses.value)
                    _isLoading.value = false

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(_sampleRate.value)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun updateSettings(rate: Long, max: Int, sort: ProcessSort, filter: ProcessFilter) {
        _sampleRate.value = rate.coerceAtLeast(500)
        _maxProcesses.value = max.coerceIn(5, 100)
        _sortOption.value = sort
        _filterOption.value = filter
        // Restart if already running
        if (monitorJob?.isActive != true) {
            startMonitoring()
        }
    }

    /* ------------------------------------------------------------------ */

    private data class PsEntry(
        val pid: String,
        val user: String,
        val psCpu: Float,
        val rssKb: Float,
        val name: String
    )

    /** Parses `ps -A -o PID,USER,%CPU,RSS,ARGS` output */
    private fun parsePsOutput(output: String): List<PsEntry> {
        val lines = output.lines()
        if (lines.size < 2) return emptyList()

        val entries = mutableListOf<PsEntry>()
        for (i in 1 until lines.size) { // skip header
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            // Fields: PID USER %CPU RSS ARGS...
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 5) continue

            val pid = parts[0]
            val user = parts[1]
            val psCpu = parts[2].toFloatOrNull() ?: 0f
            val rssKb = parts[3].toFloatOrNull() ?: 0f
            val name = if (parts.size > 4) {
                parts.subList(4, parts.size).joinToString(" ").split("/").last()
            } else parts.getOrNull(4) ?: "?"

            entries.add(PsEntry(pid, user, psCpu, rssKb, name))
        }
        return entries
    }

    /** Reads `/proc/stat` (aggregate) + `/proc/{pid}/stat` for a list of PIDs */
    data class CpuStat(val total: Long, val procTimes: Map<String, Long>)

    private suspend fun readTargetedCpuStats(pids: List<String>): CpuStat {
        // Build a single shell command that reads /proc/stat and each /proc/{pid}/stat
        val sb = StringBuilder().apply {
            append("cat /proc/stat | grep '^cpu ' ")
            for (pid in pids) {
                append("; cat /proc/$pid/stat 2>/dev/null || true")
            }
        }
        val output = rootRepository.run(sb.toString())

        var totalCpu: Long = 0
        val procMap = mutableMapOf<String, Long>()
        var parsingProcs = false

        for (line in output.lines()) {
            if (line.startsWith("cpu ")) {
                // Aggregate CPU line: cpu  user nice sys idle iowait irq softirq ...
                val parts = line.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                for (i in 1 until parts.size) {
                    totalCpu += parts[i].toLongOrNull() ?: 0L
                }
                parsingProcs = true
                continue
            }

            if (!parsingProcs) continue

            val closeParenIdx = line.lastIndexOf(')')
            if (closeParenIdx == -1) continue

            val pidStr = line.substringBefore('(').trim()
            val statsStr = line.substring(closeParenIdx + 2)
            val stats = statsStr.split(" ")

            if (stats.size > 12) {
                val utime = stats[11].toLongOrNull() ?: 0L
                val stime = stats[12].toLongOrNull() ?: 0L
                procMap[pidStr] = utime + stime
            }
        }

        return CpuStat(totalCpu, procMap)
    }

    /** Computes CPU percentage from delta between two samples */
    private fun computeDeltaCpu(
        prev: CpuStat,
        curr: CpuStat
    ): Map<String, Float> {
        val (prevTotal, prevMap) = prev
        val totalDelta = curr.total - prevTotal
        if (totalDelta <= 0 || prevMap.isEmpty()) return emptyMap()

        return curr.procTimes.mapNotNull { (pid, currTicks) ->
            val prevTicks = prevMap[pid] ?: 0L
            val delta = currTicks - prevTicks
            if (delta > 0) {
                val cpuUsage = (delta.toFloat() / totalDelta.toFloat()) * 100f
                pid to cpuUsage
            } else {
                null
            }
        }.toMap()
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
