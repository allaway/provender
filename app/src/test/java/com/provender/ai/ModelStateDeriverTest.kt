package com.provender.ai

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStateDeriverTest {

    private fun derive(
        fileExists: Boolean = false,
        fileSizeBytes: Long = 0,
        workState: WorkInfo.State? = null,
        workProgressPercent: Int? = null,
        workErrorMessage: String? = null,
        importProgressPercent: Int? = null,
    ) = ModelStateDeriver.derive(
        fileExists, fileSizeBytes, workState, workProgressPercent, workErrorMessage,
        importProgressPercent,
    )

    @Test
    fun `no file and no work means not downloaded`() {
        assertEquals(ModelState.NotDownloaded, derive())
    }

    @Test
    fun `existing file wins over stale work records`() {
        assertEquals(
            ModelState.Downloaded(42L),
            derive(fileExists = true, fileSizeBytes = 42L, workState = WorkInfo.State.SUCCEEDED),
        )
    }

    @Test
    fun `running work reports progress`() {
        assertEquals(
            ModelState.Downloading(37),
            derive(workState = WorkInfo.State.RUNNING, workProgressPercent = 37),
        )
    }

    @Test
    fun `enqueued work shows indeterminate progress`() {
        assertEquals(ModelState.Downloading(null), derive(workState = WorkInfo.State.ENQUEUED))
        assertEquals(ModelState.Downloading(null), derive(workState = WorkInfo.State.BLOCKED))
    }

    @Test
    fun `failed work surfaces the worker's error message`() {
        assertEquals(
            ModelState.Failed("HTTP 403"),
            derive(workState = WorkInfo.State.FAILED, workErrorMessage = "HTTP 403"),
        )
        assertEquals(
            ModelState.Failed("Download failed"),
            derive(workState = WorkInfo.State.FAILED),
        )
    }

    @Test
    fun `import in progress wins over everything`() {
        assertEquals(
            ModelState.Downloading(55),
            derive(
                fileExists = false,
                workState = WorkInfo.State.FAILED,
                importProgressPercent = 55,
            ),
        )
    }

    @Test
    fun `cancelled work with no file means not downloaded`() {
        assertEquals(ModelState.NotDownloaded, derive(workState = WorkInfo.State.CANCELLED))
    }
}
