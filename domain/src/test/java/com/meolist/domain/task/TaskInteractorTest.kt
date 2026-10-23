package com.meolist.domain.task

import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.testing.FakeAlarmPort
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeNotifier
import com.meolist.domain.testing.FakeReminderRepository
import com.meolist.domain.testing.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskInteractorTest {

    private val repo = FakeTaskRepository()
    private val clock = FakeClock(nowMs = 1_000L)
    private val scheduler = ReminderScheduler(FakeReminderRepository(repo), repo, FakeAlarmPort(), FakeNotifier(), clock)
    private val interactor = TaskInteractor(repo, clock, scheduler)

    @Test
    fun add_trimsTitle_setsTimestamps_andTags() = runTest {
        val id = interactor.add("  Buy milk  ", listId = 3, tagIds = setOf(1, 2))
        val task = repo.get(id)!!
        assertEquals("Buy milk", task.title)
        assertEquals(3L, task.listId)
        assertEquals(1_000L, task.createdAt)
        assertEquals(setOf(1L, 2L), repo.tagIdsOf(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun add_rejectsBlankTitle() = runTest {
        interactor.add("   ")
    }

    @Test
    fun add_subtaskInheritsParentList() = runTest {
        val parent = interactor.add("Trip", listId = 5)
        val child = interactor.add("Pack", listId = 99, parentId = parent)
        assertEquals(5L, repo.get(child)!!.listId)
    }

    @Test
    fun add_sortOrderIncreasesWithinList() = runTest {
        val a = interactor.add("a", listId = 1)
        val b = interactor.add("b", listId = 1)
        assertTrue(repo.get(b)!!.sortOrder > repo.get(a)!!.sortOrder)
    }

    @Test
    fun save_staleCopyDoesNotUndoCompletion() = runTest {
        val id = interactor.add("Write report")
        val staleCopy = repo.get(id)!!
        clock.advance(10)
        interactor.setDone(id, true)
        interactor.save(staleCopy.copy(note = "draft"))
        val stored = repo.get(id)!!
        assertTrue(stored.isDone)
        assertEquals("draft", stored.note)
    }

    @Test
    fun save_staleCopyDoesNotRestoreFromTrash() = runTest {
        val id = interactor.add("x")
        val staleCopy = repo.get(id)!!
        interactor.moveToTrash(id)
        interactor.save(staleCopy.copy(title = "y"))
        assertTrue(repo.get(id)!!.isDeleted)
    }

    @Test
    fun save_blankTitleKeepsOld_andNoChangeSkipsWrite() = runTest {
        val id = interactor.add("keep")
        val before = repo.get(id)!!
        clock.advance(500)
        interactor.save(before.copy(title = "   "))
        assertEquals(before, repo.get(id))
    }

    @Test
    fun save_listChangeMovesSubtasks_subtaskCannotChangeOwnList() = runTest {
        val parent = interactor.add("p", listId = 1)
        val child = interactor.add("c", parentId = parent)
        interactor.save(repo.get(parent)!!.copy(listId = 2))
        assertEquals(2L, repo.get(child)!!.listId)

        interactor.save(repo.get(child)!!.copy(listId = 7))
        assertEquals(2L, repo.get(child)!!.listId)
    }

    @Test
    fun setDone_togglesCompletionTime() = runTest {
        val id = interactor.add("t")
        clock.nowMs = 5_000
        interactor.setDone(id, true)
        assertEquals(5_000L, repo.get(id)!!.completedAt)
        interactor.setDone(id, false)
        assertNull(repo.get(id)!!.completedAt)
        assertEquals(TaskStatus.TODO, repo.get(id)!!.status)
    }

    @Test
    fun restoreSubtask_alsoRestoresDeletedParent() = runTest {
        val parent = interactor.add("p")
        val child = interactor.add("c", parentId = parent)
        interactor.moveToTrash(parent)
        assertTrue(repo.get(child)!!.isDeleted)

        interactor.restore(child)
        assertNull(repo.get(parent)!!.deletedAt)
        assertNull(repo.get(child)!!.deletedAt)
    }
}
