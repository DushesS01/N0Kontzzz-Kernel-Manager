package id.nkz.nokontzzzmanager.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.nkz.nokontzzzmanager.data.repository.RootRepository
import id.nkz.nokontzzzmanager.data.repository.SysfsHelper
import id.nkz.nokontzzzmanager.data.repository.KernelFeatureRepository
import id.nkz.nokontzzzmanager.data.repository.CpuMonitorProvider
import id.nkz.nokontzzzmanager.data.repository.BatteryMonitorProvider
import id.nkz.nokontzzzmanager.data.repository.MemoryMonitorProvider
import id.nkz.nokontzzzmanager.data.repository.SystemRepository
import id.nkz.nokontzzzmanager.data.repository.TuningRepository
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import id.nkz.nokontzzzmanager.data.repository.ThermalRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideTuningRepository(@ApplicationContext context: Context): TuningRepository = TuningRepository(context)

    @Provides
    @Singleton
    fun provideThermalRepository(
        @ApplicationContext context: Context,
        rootRepository: RootRepository,
        @ThermalSettings thermalDataStore: DataStore<Preferences>
    ): ThermalRepository = ThermalRepository(context, rootRepository, thermalDataStore)

    @Provides
    @Singleton
    fun provideSysfsHelper(rootRepository: RootRepository): SysfsHelper = SysfsHelper(rootRepository)

    @Provides
    @Singleton
    fun provideKernelFeatureRepository(sysfsHelper: SysfsHelper, rootRepository: RootRepository): KernelFeatureRepository =
        KernelFeatureRepository(sysfsHelper, rootRepository)

    @Provides
    @Singleton
    fun provideRootRepository(): RootRepository = RootRepository()

    @Provides
    @Singleton
    fun provideSystemRepository(
        @ApplicationContext context: Context,
        tuningRepository: TuningRepository,
        rootRepository: RootRepository,
        sysfsHelper: SysfsHelper,
        kernelFeatures: KernelFeatureRepository,
        cpuMonitor: CpuMonitorProvider,
        batteryMonitor: BatteryMonitorProvider,
        memoryMonitor: MemoryMonitorProvider,
    ): SystemRepository =
        SystemRepository(context, tuningRepository, rootRepository, sysfsHelper, kernelFeatures, cpuMonitor, batteryMonitor, memoryMonitor)

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })
    }

    @Provides
    @Singleton
    @ThermalSettings
    fun provideThermalDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("thermal_settings") })
    }

    @Provides
    @Singleton
    fun provideBatteryHistoryDatabase(@ApplicationContext context: Context): id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase::class.java,
            "battery_history_db"
        )
        .addMigrations(id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase.MIGRATION_14_15)
        .build()
    }

    @Provides
    @Singleton
    fun provideBatteryGraphDao(database: id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase): id.nkz.nokontzzzmanager.data.database.BatteryGraphDao {
        return database.batteryGraphDao()
    }

    @Provides
    @Singleton
    fun provideBatteryGraphRepository(dao: id.nkz.nokontzzzmanager.data.database.BatteryGraphDao): id.nkz.nokontzzzmanager.data.repository.BatteryGraphRepository {
        return id.nkz.nokontzzzmanager.data.repository.BatteryGraphRepository(dao)
    }

    @Provides
    @Singleton
    fun provideAppProfileDao(database: id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase): id.nkz.nokontzzzmanager.data.database.AppProfileDao {
        return database.appProfileDao()
    }

    @Provides
    @Singleton
    fun provideAppProfileRepository(dao: id.nkz.nokontzzzmanager.data.database.AppProfileDao): id.nkz.nokontzzzmanager.data.repository.AppProfileRepository {
        return id.nkz.nokontzzzmanager.data.repository.AppProfileRepository(dao)
    }

    @Provides
    @Singleton
    fun provideCustomTunableDao(database: id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase): id.nkz.nokontzzzmanager.data.database.CustomTunableDao {
        return database.customTunableDao()
    }

    @Provides
    @Singleton
    fun provideCustomTunableRepository(
        dao: id.nkz.nokontzzzmanager.data.database.CustomTunableDao,
        rootRepository: RootRepository
    ): id.nkz.nokontzzzmanager.data.repository.CustomTunableRepository {
        return id.nkz.nokontzzzmanager.data.repository.CustomTunableRepository(dao, rootRepository)
    }

    @Provides
    @Singleton
    fun provideGameDao(database: id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase): id.nkz.nokontzzzmanager.data.database.GameDao {
        return database.gameDao()
    }

    @Provides
    @Singleton
    fun provideGameRepository(dao: id.nkz.nokontzzzmanager.data.database.GameDao): id.nkz.nokontzzzmanager.data.repository.GameRepository {
        return id.nkz.nokontzzzmanager.data.repository.GameRepository(dao)
    }

    @Provides
    @Singleton
    fun provideBenchmarkDao(database: id.nkz.nokontzzzmanager.data.database.BatteryHistoryDatabase): id.nkz.nokontzzzmanager.data.database.BenchmarkDao {
        return database.benchmarkDao()
    }

    @Provides
    @Singleton
    fun provideBenchmarkRepository(dao: id.nkz.nokontzzzmanager.data.database.BenchmarkDao): id.nkz.nokontzzzmanager.data.repository.BenchmarkRepository {
        return id.nkz.nokontzzzmanager.data.repository.BenchmarkRepository(dao)
    }
}