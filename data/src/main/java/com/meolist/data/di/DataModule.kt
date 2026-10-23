package com.meolist.data.di

import android.content.Context
import androidx.room.Room
import com.meolist.data.db.MeoDatabase
import com.meolist.data.db.dao.TagDao
import com.meolist.data.db.dao.TaskDao
import com.meolist.data.db.dao.TaskListDao
import com.meolist.data.prefs.DataStoreAppPreferences
import com.meolist.data.repo.TagRepositoryImpl
import com.meolist.domain.settings.AppPreferences
import com.meolist.data.repo.TaskListRepositoryImpl
import com.meolist.data.repo.TaskRepositoryImpl
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.SystemClock
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MeoDatabase =
        Room.databaseBuilder(context, MeoDatabase::class.java, MeoDatabase.NAME)
            // Schema v1 is still evolving during development; frozen with real migrations before release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun taskDao(db: MeoDatabase): TaskDao = db.taskDao()
    @Provides fun taskListDao(db: MeoDatabase): TaskListDao = db.taskListDao()
    @Provides fun tagDao(db: MeoDatabase): TagDao = db.tagDao()

    @Provides
    @Singleton
    fun clock(): Clock = SystemClock()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun taskRepository(impl: TaskRepositoryImpl): TaskRepository
    @Binds abstract fun taskListRepository(impl: TaskListRepositoryImpl): TaskListRepository
    @Binds abstract fun tagRepository(impl: TagRepositoryImpl): TagRepository
    @Binds abstract fun appPreferences(impl: DataStoreAppPreferences): AppPreferences
}
