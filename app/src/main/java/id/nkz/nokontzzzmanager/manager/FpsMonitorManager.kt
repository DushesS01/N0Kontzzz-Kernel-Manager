package id.nkz.nokontzzzmanager.manager

import android.util.Log
import id.nkz.nokontzzzmanager.data.repository.RootRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FpsData(
    val currentFps: Float = 0f,
    val fps1Low: Float = 0f,
    val fps01Low: Float = 0f,
    val frameTimeMs: Float = 0f,
    val jankCount: Int = 0,
    val batteryLevel: Int = 0,
    val batteryTemp: Float = 0f,
    val isTracking: Boolean = false,
    val isBenchmarking: Boolean = false,
    val benchmarkStartTime: Long = 0L,
    val currentBenchmarkDuration: Long = 0L,
    val isAutoStopped: Boolean = false
)

@Singleton
class FpsMonitorManager @Inject constructor(
    private val rootRepository: RootRepository,
    private val systemRepository: id.nkz.nokontzzzmanager.data.repository.SystemRepository
) {
    companion object {
        const val MAX_BENCHMARK_DURATION_MS = 60 * 60 * 1000L
        private const val LAYER_CACHE_TTL_MS = 30_000L
        private const val DELAY_FAST_MS = 500L
        private const val DELAY_STABLE_MS = 1000L
    }

    private val _fpsData = MutableStateFlow(FpsData())
    val fpsData: StateFlow<FpsData> = _fpsData

    private val _autoStopEvent = MutableSharedFlow<Unit>()
    val autoStopEvent: SharedFlow<Unit> = _autoStopEvent.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var isMonitoring = false
    private var currentPackageName: String? = null
    
    // Benchmarking data
    private var isBenchmarking = false
    private var benchmarkStartTime = 0L
    private var lastTimestampNs = 0L
    private var lastFrameEndNs = 0L
    private val recordedFrameTimes = mutableListOf<Float>()
    private val recordedCpuUsage = mutableListOf<Float>()
    private val recordedGpuUsage = mutableListOf<Float>()
    private val recordedTemp = mutableListOf<Float>()
    private val recordedCpuTemp = mutableListOf<Float>()
    private val recordedGpuFreq = mutableListOf<Int>()
    private val recordedCpuFreqLittle = mutableListOf<Int>()
    private val recordedCpuFreqBig = mutableListOf<Int>()
    private val recordedCpuFreqPrime = mutableListOf<Int>()
    private val recordedBatteryPower = mutableListOf<Float>()
    private val recordedBatteryLevel = mutableListOf<Int>()
    private var totalJankCount = 0
    private var totalBigJankCount = 0
    
    // Baseline refresh rate info
    private var refreshRate = 60f

    // ── Layer cache per package: (candidates, timestamp) ──
    private data class LayerCacheEntry(
        val candidates: List<String>,
        val timestamp: Long
    )
    private val layerCache = mutableMapOf<String, LayerCacheEntry>()

    fun startMonitoring(
        packageName: String,
        preferredLayerPattern: String? = null,
        onLayerFound: (String) -> Unit = {}
    ) {
        if (isMonitoring && currentPackageName == packageName) return
        
        Log.d("FpsMonitor", "Starting monitoring for $packageName (preferred: $preferredLayerPattern)")
        stopMonitoring()
        
        isMonitoring = true
        currentPackageName = packageName
        _fpsData.value = _fpsData.value.copy(isTracking = true)

        monitorJob = scope.launch {
            // Clear old latency data to ensure we get fresh frames
            rootRepository.run("dumpsys SurfaceFlinger --latency-clear")
            delay(500)

            refreshRate = getRefreshRate()
            Log.d("FpsMonitor", "Detected base refresh rate: $refreshRate")

            var currentLayer: String? = null
            var candidateIndex = 0
            var allCandidates = listOf<String>()
            var layerValidated = false
            var currentLayerFailureCount = 0
            var layerStableSince = 0L // last time layer was validated or changed

            lastTimestampNs = 0L
            lastFrameEndNs = 0L

            // ── Metrics job is now started/stopped per benchmarking state ──
            var metricsJob: kotlinx.coroutines.Job? = null

            fun startMetricsJobIfNeeded() {
                if (metricsJob?.isActive == true) return
                metricsJob = launch { metricsPollingLoop() }
            }

            fun stopMetricsJob() {
                metricsJob?.cancel()
                metricsJob = null
            }

            var adaptiveDelay = DELAY_FAST_MS

            while (isMonitoring && isActive) {
                // Refresh candidates if we don't have a working layer
                if (currentLayer == null) {
                    val cached = getCachedCandidateLayers(packageName, preferredLayerPattern)
                    allCandidates = cached
                    candidateIndex = 0
                    if (allCandidates.isNotEmpty()) {
                        currentLayer = cleanLayerName(allCandidates[candidateIndex])
                        Log.d("FpsMonitor", "Trying candidate: $currentLayer")
                        lastTimestampNs = 0L
                        lastFrameEndNs = 0L
                    }
                }

                if (currentLayer != null) {
                    val latencyData = getSurfaceFlingerLatency(currentLayer)
                    val success = processLatencyData(latencyData)

                    if (!success) {
                        currentLayerFailureCount++

                        val maxRetries = if (preferredLayerPattern != null && currentLayer.contains(preferredLayerPattern)) 5 else 2

                        if (currentLayerFailureCount >= maxRetries) {
                            Log.v("FpsMonitor", "Layer $currentLayer consistently empty, trying next.")
                            candidateIndex++
                            currentLayerFailureCount = 0
                            lastTimestampNs = 0L
                            lastFrameEndNs = 0L

                            if (candidateIndex < allCandidates.size) {
                                currentLayer = cleanLayerName(allCandidates[candidateIndex])
                                Log.v("FpsMonitor", "Switching to next candidate: $currentLayer")
                            } else {
                                currentLayer = null
                                delay(2000)
                                continue // skip the loop-delay at the bottom
                            }
                        }
                        // Still on fast pace when trying to stabilise
                        adaptiveDelay = DELAY_FAST_MS
                    } else {
                        currentLayerFailureCount = 0
                        if (!layerValidated) {
                            val fullRaw = allCandidates[candidateIndex]
                            val pattern = extractPattern(fullRaw)
                            if (pattern != null) {
                                Log.i("FpsMonitor", "Valid layer found: $pattern. Saving.")
                                onLayerFound(pattern)
                                layerValidated = true
                                layerStableSince = System.currentTimeMillis()
                            }
                        } else if (layerStableSince > 0L &&
                            System.currentTimeMillis() - layerStableSince > 5000L
                        ) {
                            // Layer has been stable for 5+ s – slow down
                            adaptiveDelay = DELAY_STABLE_MS
                        }
                    }
                } else {
                    Log.w("FpsMonitor", "No valid layers for $packageName")
                    _fpsData.value = _fpsData.value.copy(currentFps = 0f)
                    delay(2000)
                    continue
                }

                if (isBenchmarking) {
                    val duration = System.currentTimeMillis() - benchmarkStartTime
                    _fpsData.value = _fpsData.value.copy(currentBenchmarkDuration = duration)
                    
                    if (duration >= MAX_BENCHMARK_DURATION_MS) {
                        Log.w("FpsMonitor", "Benchmark hit max time. Auto-stopping.")
                        scope.launch { _autoStopEvent.emit(Unit) }
                        stopBenchmarking()
                    }
                    if (metricsJob?.isActive != true) startMetricsJobIfNeeded()
                } else {
                    if (metricsJob?.isActive == true) stopMetricsJob()
                }

                delay(adaptiveDelay.coerceAtMost(DELAY_FAST_MS).coerceAtLeast(DELAY_STABLE_MS))
            }

            stopMetricsJob()
        }
    }

    /** Polling for CPU, GPU, battery — only runs while benchmarking */
    private suspend fun metricsPollingLoop() {
        // Pre-calculate clusters core mapping once
        val clusters = try {
            systemRepository.getCpuClusters()
        } catch (_: Exception) { emptyList() }

        val cores = Runtime.getRuntime().availableProcessors()
        val littleClusterCores = mutableListOf<Int>()
        val bigClusterCores = mutableListOf<Int>()
        val primeClusterCores = mutableListOf<Int>()

        try {
            val coreFreqRanges = mutableMapOf<Int, Pair<Int, Int>>()
            for (i in 0 until cores) {
                val minPath = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq"
                val maxPath = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
                
                val minStr = rootRepository.run("cat $minPath 2>/dev/null || true").trim()
                val maxStr = rootRepository.run("cat $maxPath 2>/dev/null || true").trim()
                
                val min = minStr.toIntOrNull() ?: 0
                val max = maxStr.toIntOrNull() ?: 0
                if (max > 0) coreFreqRanges[i] = Pair(min, max)
            }
            
            val frequencyGroups = coreFreqRanges.values.distinct().sortedBy { it.second }
            frequencyGroups.forEachIndexed { index, pair ->
                val coresInThisGroup = coreFreqRanges.filter { it.value == pair }.keys
                when {
                    index == 0 -> littleClusterCores.addAll(coresInThisGroup)
                    index == frequencyGroups.size - 1 && frequencyGroups.size > 1 -> primeClusterCores.addAll(coresInThisGroup)
                    else -> bigClusterCores.addAll(coresInThisGroup)
                }
            }
        } catch (e: Exception) {
            Log.e("FpsMonitor", "Error identifying clusters for metricsJob", e)
        }

        while (currentCoroutineContext().isActive) {
            try {
                val batteryInfo = systemRepository.getBatteryInfo()
                
                _fpsData.value = _fpsData.value.copy(
                    batteryLevel = batteryInfo.level,
                    batteryTemp = batteryInfo.temp
                )

                if (isBenchmarking) {
                    val cpuInfo = systemRepository.getCpuRealtime()
                    val gpuInfo = systemRepository.getGpuRealtime()

                    recordedCpuUsage.add(cpuInfo.cpuLoadPercentage ?: 0f)
                    recordedCpuTemp.add(cpuInfo.temp)
                    recordedGpuUsage.add(gpuInfo.usagePercentage ?: 0f)
                    recordedGpuFreq.add(gpuInfo.currentFreq)
                    recordedTemp.add(batteryInfo.temp)
                    
                    if (littleClusterCores.isNotEmpty()) {
                        val avgFreq = littleClusterCores.map { cpuInfo.freqs.getOrNull(it) ?: 0 }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                        recordedCpuFreqLittle.add(avgFreq)
                    }
                    if (bigClusterCores.isNotEmpty()) {
                        val avgFreq = bigClusterCores.map { cpuInfo.freqs.getOrNull(it) ?: 0 }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                        recordedCpuFreqBig.add(avgFreq)
                    }
                    if (primeClusterCores.isNotEmpty()) {
                        val avgFreq = primeClusterCores.map { cpuInfo.freqs.getOrNull(it) ?: 0 }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                        recordedCpuFreqPrime.add(avgFreq)
                    }
                    
                    recordedBatteryPower.add(batteryInfo.chargingWattage)
                    recordedBatteryLevel.add(batteryInfo.level)
                }
            } catch (e: Exception) {
                Log.e("FpsMonitor", "Error polling metrics", e)
            }
            delay(1000)
        }
    }

    fun stopMonitoring() {
        Log.d("FpsMonitor", "Stopping monitoring")
        if (isBenchmarking) stopBenchmarking()
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        currentPackageName = null
        _fpsData.value = _fpsData.value.copy(isTracking = false, currentFps = 0f, fps1Low = 0f, fps01Low = 0f)
    }

    fun startBenchmarking() {
        if (isBenchmarking || !isMonitoring) return
        isBenchmarking = true
        benchmarkStartTime = System.currentTimeMillis()
        recordedFrameTimes.clear()
        recordedCpuUsage.clear()
        recordedGpuUsage.clear()
        recordedTemp.clear()
        recordedCpuTemp.clear()
        recordedGpuFreq.clear()
        recordedCpuFreqLittle.clear()
        recordedCpuFreqBig.clear()
        recordedCpuFreqPrime.clear()
        recordedBatteryPower.clear()
        recordedBatteryLevel.clear()
        totalJankCount = 0
        totalBigJankCount = 0
        _fpsData.value = _fpsData.value.copy(
            isBenchmarking = true,
            benchmarkStartTime = benchmarkStartTime,
            currentBenchmarkDuration = 0L
        )
    }

    data class BenchmarkResult(
        val packageName: String,
        val startTime: Long,
        val durationMs: Long,
        val avgFps: Float,
        val fps1Low: Float,
        val fps01Low: Float,
        val jankCount: Int,
        val bigJankCount: Int,
        val avgCpuUsage: Float,
        val avgGpuUsage: Float,
        val avgTemp: Float,
        val maxTemp: Float,
        val avgPower: Float,
        val maxFps: Float,
        val minFps: Float,
        val fpsVariance: Float,
        val frameTimes: List<Float>,
        val cpuUsageHistory: List<Float>,
        val gpuUsageHistory: List<Float>,
        val tempHistory: List<Float>,
        val cpuTempHistory: List<Float>,
        val gpuFreqHistory: List<Int>,
        val cpuFreqLittleHistory: List<Int>,
        val cpuFreqBigHistory: List<Int>,
        val cpuFreqPrimeHistory: List<Int>,
        val batteryPowerHistory: List<Float>,
        val batteryLevelHistory: List<Int>
    )

    fun stopBenchmarking(): BenchmarkResult? {
        if (!isBenchmarking) return null
        
        val durationMs = System.currentTimeMillis() - benchmarkStartTime
        isBenchmarking = false
        _fpsData.value = _fpsData.value.copy(isBenchmarking = false)
        
        if (recordedFrameTimes.isEmpty()) return null

        val fpsPerSecond = mutableListOf<Float>()
        var currentWindowMs = 0f
        var frameCount = 0
        for (interval in recordedFrameTimes) {
            currentWindowMs += interval
            frameCount++
            if (currentWindowMs >= 1000f) {
                fpsPerSecond.add(frameCount.toFloat().coerceAtMost(refreshRate))
                currentWindowMs -= 1000f
                frameCount = 0
            }
        }
        if (frameCount > 0 && currentWindowMs > 200f) {
            fpsPerSecond.add((frameCount * (1000f / currentWindowMs)).coerceAtMost(refreshRate))
        }

        val avgFrameTime = recordedFrameTimes.average().toFloat()
        val avgFps = if (fpsPerSecond.isNotEmpty()) fpsPerSecond.average().toFloat() else 1000f / avgFrameTime
        
        val maxFps = if (fpsPerSecond.isNotEmpty()) fpsPerSecond.maxOrNull() ?: 0f else 0f
        val minFps = if (fpsPerSecond.isNotEmpty()) fpsPerSecond.minOrNull() ?: 0f else 0f
        
        val variance = if (fpsPerSecond.size > 1) {
            val mean = fpsPerSecond.average()
            fpsPerSecond.map { (it - mean) * (it - mean) }.average().toFloat()
        } else 0f

        val sorted = recordedFrameTimes.sortedDescending()
        val p1Index = (sorted.size * 0.01).toInt().coerceAtMost(sorted.size - 1)
        val p01Index = (sorted.size * 0.001).toInt().coerceAtMost(sorted.size - 1)
        
        val fps1Low = 1000f / sorted[p1Index].coerceAtLeast(1f)
        val fps01Low = 1000f / sorted[p01Index].coerceAtLeast(1f)

        return BenchmarkResult(
            packageName = currentPackageName ?: "unknown",
            startTime = benchmarkStartTime,
            durationMs = durationMs,
            avgFps = avgFps.coerceAtMost(refreshRate),
            fps1Low = fps1Low.coerceAtMost(refreshRate),
            fps01Low = fps01Low.coerceAtMost(refreshRate),
            jankCount = totalJankCount,
            bigJankCount = totalBigJankCount,
            avgCpuUsage = if (recordedCpuUsage.isNotEmpty()) recordedCpuUsage.average().toFloat() else 0f,
            avgGpuUsage = if (recordedGpuUsage.isNotEmpty()) recordedGpuUsage.average().toFloat() else 0f,
            avgTemp = if (recordedTemp.isNotEmpty()) recordedTemp.average().toFloat() else 0f,
            maxTemp = if (recordedTemp.isNotEmpty()) recordedTemp.maxOrNull() ?: 0f else 0f,
            avgPower = if (recordedBatteryPower.isNotEmpty()) recordedBatteryPower.average().toFloat() else 0f,
            maxFps = maxFps,
            minFps = minFps,
            fpsVariance = variance,
            frameTimes = recordedFrameTimes.toList(),
            cpuUsageHistory = recordedCpuUsage.toList(),
            gpuUsageHistory = recordedGpuUsage.toList(),
            tempHistory = recordedTemp.toList(),
            cpuTempHistory = recordedCpuTemp.toList(),
            gpuFreqHistory = recordedGpuFreq.toList(),
            cpuFreqLittleHistory = recordedCpuFreqLittle.toList(),
            cpuFreqBigHistory = recordedCpuFreqBig.toList(),
            cpuFreqPrimeHistory = recordedCpuFreqPrime.toList(),
            batteryPowerHistory = recordedBatteryPower.toList(),
            batteryLevelHistory = recordedBatteryLevel.toList()
        )
    }

    private suspend fun getRefreshRate(): Float {
        return try {
            val result = rootRepository.run("dumpsys display | grep -E \"mDefaultMode|refreshRate\" | grep -oE \"[0-9]+(\\.[0-9]+)?\"")
            val fps = result.trim().split("\n").firstOrNull { it.toFloatOrNull() ?: 0f > 10f }?.toFloatOrNull()
            
            if (fps != null && fps < 1000f) {
                fps
            } else {
                60f
            }
        } catch (e: Exception) {
            60f
        }
    }

    private fun extractPattern(rawName: String): String? {
        var cleaned = if (rawName.startsWith("RequestedLayerState{") && rawName.endsWith("}")) {
            rawName.substring("RequestedLayerState{".length, rawName.length - 1)
        } else rawName
        
        val firstSpace = cleaned.indexOf(' ')
        if (firstSpace != -1 && firstSpace <= 10) {
            val prefix = cleaned.substring(0, firstSpace)
            if (prefix.all { it.isLetterOrDigit() }) {
                cleaned = cleaned.substring(firstSpace + 1).trim()
            }
        }

        val index = cleaned.indexOf('#')
        return if (index != -1) cleaned.substring(0, index).trim() else null
    }

    /** Cached wrapper around raw candidate discovery */
    private suspend fun getCachedCandidateLayers(
        packageName: String,
        preferredPattern: String? = null
    ): List<String> {
        val now = System.currentTimeMillis()
        val entry = layerCache[packageName]
        if (entry != null && (now - entry.timestamp) < LAYER_CACHE_TTL_MS) {
            Log.d("FpsMonitor", "Reusing cached candidate layers for $packageName")
            return entry.candidates
        }
        val fresh = getCandidateLayersRaw(packageName, preferredPattern)
        if (fresh.isNotEmpty()) {
            layerCache[packageName] = LayerCacheEntry(fresh, now)
        }
        return fresh
    }

    private suspend fun getCandidateLayersRaw(packageName: String, preferredPattern: String? = null): List<String> {
        return try {
            val output = rootRepository.run("dumpsys SurfaceFlinger --list")
            val lines = output.lines().filter { it.isNotBlank() }
            
            val candidateLayers = lines.filter { it.contains(packageName, ignoreCase = true) }
            
            val excludedKeywords = listOf(
                "Snapshot", "Background for", "ActivityRecord", 
                "ActivityRecordInputSink", "Splash Screen", 
                "animation-leash", "Bounds for"
            )

            val validCandidates = candidateLayers.filter { layer ->
                excludedKeywords.none { keyword -> layer.contains(keyword, ignoreCase = true) }
            }.sortedWith(
                compareByDescending<String> { preferredPattern != null && it.contains(preferredPattern) }
                .thenByDescending { it.contains("BLAST", ignoreCase = true) }
                .thenByDescending { it.contains("SurfaceView", ignoreCase = true) }
                .thenByDescending { it.contains("#") }
            )

            Log.d("FpsMonitor", "Found ${validCandidates.size} potential rendering layers for $packageName")
            validCandidates
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getActiveLayerName(packageName: String): String? {
        val candidates = getCachedCandidateLayers(packageName)
        return if (candidates.isNotEmpty()) cleanLayerName(candidates.first()) else null
    }

    private fun cleanLayerName(rawName: String): String {
        var name = rawName.trim()
        
        if (name.startsWith("RequestedLayerState{") && name.endsWith("}")) {
            name = name.substring("RequestedLayerState{".length, name.length - 1)
        }
        
        val metadataKeywords = listOf("parentId=", "relativeParentId=", "z=", "layerStack=")
        var firstMetadataIndex = name.length
        for (keyword in metadataKeywords) {
            val index = name.indexOf(keyword)
            if (index != -1 && index < firstMetadataIndex) {
                firstMetadataIndex = index
            }
        }
        
        return name.substring(0, firstMetadataIndex).trim()
    }

    private suspend fun getSurfaceFlingerLatency(layerName: String): String {
        return try {
            val data = rootRepository.run("dumpsys SurfaceFlinger --latency \"$layerName\"")
            if (data.isBlank()) {
                Log.w("FpsMonitor", "Empty latency data for layer: $layerName")
            }
            data
        } catch (e: Exception) {
            ""
        }
    }

    private fun processLatencyData(rawData: String): Boolean {
        if (rawData.isBlank()) return false

        val lines = rawData.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return false

        try {
            val firstLine = lines.firstOrNull()
            val refreshPeriodNs = firstLine?.toLongOrNull() ?: 0L
            
            if (refreshPeriodNs <= 0) {
                Log.w("FpsMonitor", "Invalid refresh period in latency data: $firstLine")
                return false
            }

            val dynamicRefreshRate = 1_000_000_000f / refreshPeriodNs.toFloat()
            val expectedFrameTimeMs = refreshPeriodNs / 1_000_000f
            
            if (dynamicRefreshRate > refreshRate && dynamicRefreshRate < 241f) {
                refreshRate = dynamicRefreshRate
                Log.d("FpsMonitor", "Updated refresh rate baseline to $refreshRate")
            }

            val frameIntervals = mutableListOf<Float>()
            var janks = 0
            var newLastTimestampNs = lastTimestampNs

            for (i in 1 until lines.size) {
                val parts = lines[i].trim().split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val start = parts[0].toLongOrNull() ?: 0L
                    val end = parts[2].toLongOrNull() ?: 0L

                    if (start == 0L || end == 0L || end == Long.MAX_VALUE) continue
                    
                    if (end <= lastTimestampNs) continue

                    if (lastFrameEndNs != 0L) {
                        val intervalMs = (end - lastFrameEndNs) / 1_000_000f
                        if (intervalMs > 0 && intervalMs < 500) {
                            frameIntervals.add(intervalMs)
                            if (isBenchmarking) {
                                recordedFrameTimes.add(intervalMs)
                            }
                            
                            if (intervalMs > expectedFrameTimeMs * 1.5f) {
                                janks++
                                if (isBenchmarking) totalJankCount++
                                
                                if (intervalMs > expectedFrameTimeMs * 3f) {
                                    if (isBenchmarking) totalBigJankCount++
                                }
                            }
                        }
                    }
                    lastFrameEndNs = end
                    if (end > newLastTimestampNs) newLastTimestampNs = end
                }
            }

            lastTimestampNs = newLastTimestampNs

            if (frameIntervals.isNotEmpty()) {
                val avgInterval = frameIntervals.average().toFloat()
                val fps = 1000f / avgInterval
                
                val sorted = frameIntervals.sortedDescending()
                val top1PercentIndex = (sorted.size * 0.01).toInt().coerceAtMost(sorted.size - 1)
                val top01PercentIndex = (sorted.size * 0.001).toInt().coerceAtMost(sorted.size - 1)
                
                val fps1Low = 1000f / sorted[top1PercentIndex].coerceAtLeast(1f)
                val fps01Low = 1000f / sorted[top01PercentIndex].coerceAtLeast(1f)

                _fpsData.value = _fpsData.value.copy(
                    currentFps = fps.coerceAtMost(dynamicRefreshRate + 1f),
                    fps1Low = fps1Low.coerceAtMost(dynamicRefreshRate),
                    fps01Low = fps01Low.coerceAtMost(dynamicRefreshRate),
                    frameTimeMs = avgInterval,
                    jankCount = janks
                )
                return true
            } else {
                Log.v("FpsMonitor", "No valid frames parsed from ${lines.size} lines")
                return false
            }
        } catch (e: Exception) {
            Log.e("FpsMonitor", "Error parsing latency data", e)
            return false
        }
    }
}
