package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.goodcompany.core.testing.FakeAppUpdateSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckForUpdateUseCaseTest {
    private val published = AppUpdate(versionCode = 42, versionName = "1.0.0-test.42", apkUrl = "https://example.com/a.apk")
    private val source = FakeAppUpdateSource(published)
    private val check = CheckForUpdateUseCase(source)

    @Test
    fun aNewerPublishedBuildIsOffered() = runTest {
        assertEquals(published, check(41).getOrThrow())
    }

    @Test
    fun theSameOrAnOlderPublishedBuildIsNot() = runTest {
        assertNull(check(42).getOrThrow())
        assertNull(check(43).getOrThrow())
    }

    @Test
    fun nothingPublishedMeansNothingToOffer() = runTest {
        source.newest = null
        assertNull(check(1).getOrThrow())
    }

    @Test
    fun anUnreachableSourceIsAFailureNotACrash() = runTest {
        source.failure = IllegalStateException("HTTP 503")
        assertTrue(check(1).isFailure)
    }
}
