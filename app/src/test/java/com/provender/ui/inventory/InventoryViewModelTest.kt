package com.provender.ui.inventory

import app.cash.turbine.test
import com.provender.data.model.Category
import com.provender.data.repository.ItemDraft
import com.provender.testing.FakeInventoryRepository
import com.provender.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InventoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeInventoryRepository
    private lateinit var viewModel: InventoryViewModel

    @Before
    fun setUp() {
        repository = FakeInventoryRepository()
        viewModel = InventoryViewModel(repository)
    }

    private suspend fun addRice(): Long =
        repository.addItem(ItemDraft(name = "Rice", quantity = 2.0, unit = "kg", locationId = 1))

    @Test
    fun `exposes sections grouped by location once loaded`() = runTest {
        addRice()

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(listOf("Pantry", "Fridge"), loaded.sections.map { it.location.name })
            assertEquals(listOf("Rice"), loaded.sections.first().items.map { it.name })
            assertEquals(2, loaded.locations.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query switches state into search mode with matching results`() = runTest {
        addRice()
        repository.addItem(ItemDraft(name = "Milk", quantity = 1.0, unit = "l", locationId = 2))

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.onQueryChange("ric")

            val searching = awaitUntil { it.isSearching && it.searchResults.isNotEmpty() }
            assertEquals(listOf("Rice"), searching.searchResults.map { it.name })

            viewModel.onQueryChange("")
            val browsing = awaitUntil { !it.isSearching }
            assertTrue(browsing.searchResults.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add flow opens editor, saves through repository, then closes editor`() = runTest {
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            viewModel.onAddClick()
            val editing = awaitUntil { it.editor != null }
            assertNull(editing.editor!!.existing)

            viewModel.onSave(
                ItemDraft(name = "Beans", category = Category.CANNED, quantity = 3.0, unit = "can", locationId = 1),
            )

            val saved = awaitUntil { it.editor == null && it.sections.first().items.isNotEmpty() }
            assertEquals(listOf("Beans"), saved.sections.first().items.map { it.name })
            assertEquals(listOf("add:Beans"), repository.mutations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving with an item open updates instead of adding`() = runTest {
        val id = addRice()

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            viewModel.onItemClick(loaded.sections.first().items.single())
            awaitUntil { it.editor?.existing?.id == id }

            viewModel.onSave(ItemDraft(name = "Brown rice", quantity = 2.0, unit = "kg", locationId = 1))

            awaitUntil { it.editor == null }
            assertEquals(listOf("update:$id:Brown rice"), repository.mutations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete goes through repository and closes the editor`() = runTest {
        val id = addRice()

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            val item = loaded.sections.first().items.single()
            viewModel.onItemClick(item)
            awaitUntil { it.editor != null }

            viewModel.onDelete(item)

            val after = awaitUntil { it.editor == null && it.sections.first().items.isEmpty() }
            assertNotNull(after)
            assertEquals(listOf("delete:$id"), repository.mutations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `staple toggle flips the flag`() = runTest {
        val id = addRice()

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }

            viewModel.onToggleStaple(loaded.sections.first().items.single())

            val toggled = awaitUntil { it.sections.first().items.singleOrNull()?.isStaple == true }
            assertTrue(toggled.sections.first().items.single().isStaple)
            assertEquals(listOf("staple:$id:true"), repository.mutations)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Skips intermediate emissions until [predicate] holds; fails the test on channel close. */
private suspend fun <T> app.cash.turbine.TurbineTestContext<T>.awaitUntil(
    predicate: (T) -> Boolean,
): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
