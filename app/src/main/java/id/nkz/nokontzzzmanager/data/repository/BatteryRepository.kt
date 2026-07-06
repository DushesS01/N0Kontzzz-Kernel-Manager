package id.nkz.nokontzzzmanager.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import id.nkz.nokontzzzmanager.data.model.BatteryInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    private val context: Context
) {
    private val batteryManager: BatteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    /**
     * Emits [BatteryInfo] every time the battery state changes via
     * [Intent.ACTION_BATTERY_CHANGED], without polling.
     *
     * We register a single BroadcastReceiver inside a callbackFlow and
     * emit on every broadcast – the OS fires this intent whenever the
     * battery status, level, charging state, etc. actually change, so
     * there is no busy-waiting.
     */
    fun getBatteryInfo(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val data = extractFromIntent(intent)
                if (data != null) {
                    trySend(data)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Register *synchronously* – this triggers the latest sticky intent immediately
        val stickyIntent = context.registerReceiver(receiver, filter)
        // The very first emission (if any) comes from the sticky intent
        stickyIntent?.let {
            extractFromIntent(it)?.let { batteryInfo ->
                trySend(batteryInfo)
            }
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Receiver already unregistered
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun extractFromIntent(intent: Intent?): BatteryInfo? {
        intent ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val tempCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        // Plugged: 0 = unplugged, 1 = AC, 2 = USB wireless, etc.
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

        // Charging current (µA → mA)
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (current != Int.MIN_VALUE) current / 1000f else 0f
        val chargingWattage = abs(voltage * current / 1_000_000f)

        val isCharging = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL -> true
            status == BatteryManager.BATTERY_STATUS_DISCHARGING -> false
            plugged == 0 -> false
            current > 5000 -> true
            plugged != 0 -> true
            else -> false
        }

        return BatteryInfo(
            level = if (level >= 0 && scale > 0) (level * 100 / scale) else -1,
            temp = tempCelsius,
            voltage = voltage,
            isCharging = isCharging,
            current = currentMa,
            chargingWattage = if (isCharging) chargingWattage else 0f,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "",
            health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
                else -> "Unknown"
            },
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }
        )
    }
}
