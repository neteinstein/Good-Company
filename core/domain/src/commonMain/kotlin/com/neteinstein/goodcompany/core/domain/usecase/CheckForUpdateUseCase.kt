package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.repository.AppUpdateSource
import kotlin.coroutines.cancellation.CancellationException

/** Asks the update source whether a build newer than the installed one exists. */
class CheckForUpdateUseCase(private val source: AppUpdateSource) {
    /** The newer build, null when the installed one is current, or a failure when the source can't be reached. */
    suspend operator fun invoke(installedVersionCode: Long): Result<AppUpdate?> = try {
        Result.success(source.latest()?.takeIf { it.versionCode > installedVersionCode })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
