package com.marcio.workmanagerdemo

import android.util.Log
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.marcio.workmanagerdemo.AndroidTestHelper.Companion.launchActivity
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkManagerTest {

    @Rule
    @JvmField
    val rule: AndroidTestHelperRule<MainActivity> =
        AndroidTestHelperRule(MainActivity::class.java)

    @Before
    fun setup() {
        // Context of the app under test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun testRawWorkManagerWithOneTimeRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val request = OneTimeWorkRequestBuilder<RefreshTokenWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request).result.get()

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.setAllConstraintsMet(request.id)

        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertThat(workInfo.state, `is`(WorkInfo.State.SUCCEEDED))
    }

    @Test
    fun shouldRefreshTokenWhenNetworkIsAvailableAndAfterInitialDelay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PeriodicWorkRequestBuilder<RefreshTokenWorker>(4, TimeUnit.HOURS)
            .setInitialDelay(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        val jobScheduler = RefreshTokenJobScheduler(
            WorkManager.getInstance(context), request
        )

        val sharedPreferences = FakeSharedPreferences(mutableMapOf("token" to "old_token"))

        launchActivity(
            rule,
            jobScheduler,
            sharedPreferences
        )

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.apply {
            setInitialDelayMet(request.id)
            setAllConstraintsMet(request.id)
        }

        assertEquals("new_token", sharedPreferences.getString("token", ""))
    }

    @Test
    fun shouldRefreshTokenWhenNetworkIsAvailableAndScheduleIsOnTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PeriodicWorkRequestBuilder<RefreshTokenWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        val jobScheduler = RefreshTokenJobScheduler(
            WorkManager.getInstance(context), request
        )

        val sharedPreferences = FakeSharedPreferences(mutableMapOf("token" to "old_token"))

        launchActivity(
            rule,
            jobScheduler,
            sharedPreferences
        )

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.apply {
            setPeriodDelayMet(request.id)
            setAllConstraintsMet(request.id)
        }

        assertEquals("new_token", sharedPreferences.getString("token", ""))
    }

    @Test
    fun shouldNotRefreshTokenBeforeScheduledTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PeriodicWorkRequestBuilder<RefreshTokenWorker>(4, TimeUnit.HOURS)
            .setInitialDelay(4, TimeUnit.HOURS)
            .build()
        val jobScheduler = RefreshTokenJobScheduler(
            WorkManager.getInstance(context), request
        )

        val sharedPreferences = FakeSharedPreferences(mutableMapOf("token" to "old_token"))

        launchActivity(
            rule,
            jobScheduler,
            sharedPreferences
        )

        assertEquals("old_token", sharedPreferences.getString("token", ""))
    }

    @Test
    fun shouldNotRefreshTokenWhenNetworkIsUnavailableAndScheduleIsOnTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PeriodicWorkRequestBuilder<RefreshTokenWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        val jobScheduler = RefreshTokenJobScheduler(
            WorkManager.getInstance(context), request
        )

        val sharedPreferences = FakeSharedPreferences(mutableMapOf("token" to "old_token"))

        launchActivity(
            rule,
            jobScheduler,
            sharedPreferences
        )

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.setPeriodDelayMet(request.id)

        assertEquals("old_token", sharedPreferences.getString("token", ""))
    }

    @Test
    fun shouldRefreshTokenAfterScheduledTimeWhenNetworkBecomesAvailableAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PeriodicWorkRequestBuilder<RefreshTokenWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        val jobScheduler = RefreshTokenJobScheduler(
            WorkManager.getInstance(context), request
        )

        val sharedPreferences = FakeSharedPreferences(mutableMapOf("token" to "old_token"))

        launchActivity(
            rule,
            jobScheduler,
            sharedPreferences
        )

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        testDriver?.setPeriodDelayMet(request.id)

        assertEquals("old_token", sharedPreferences.getString("token", ""))

        testDriver?.setAllConstraintsMet(request.id)

        assertEquals("new_token", sharedPreferences.getString("token", ""))
    }
}
