package takagi.ru.monica.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Mdbx2ReadSessionCacheTest {
    private class Session { var closed = false }

    @Test fun listAndDetailShareAnUnlockButNoReadResultIsCached() = runTest {
        var opens = 0
        var reads = 0
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true })
        fun read() = cache.use(1, "revision", { true }, { opens++; Session() }, { "revision" }) {
            assertFalse(it.closed)
            ++reads
        }
        assertEquals(1, read())
        assertEquals(2, read())
        assertEquals(1, opens)
        cache.clear()
    }

    @Test fun fileOrCredentialChangesAndMutationsCloseThePreviousSession() = runTest {
        val opened = mutableListOf<Session>()
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true })
        fun read(revision: String) = cache.use(1, revision, { true },
            { Session().also(opened::add) }, { revision }) { assertFalse(it.closed) }
        read("original")
        read("replacement-file")
        assertTrue(opened[0].closed)
        read("changed-credential")
        assertTrue(opened[1].closed)
        cache.invalidate(1)
        assertTrue(opened[2].closed)
        read("changed-credential")
        assertEquals(4, opened.size)
        cache.clear()
        assertTrue(opened.all { it.closed })
    }

    @Test fun lockingDuringOpenCannotRepopulateTheCache() = runTest {
        var opens = 0
        val opened = mutableListOf<Session>()
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true })
        cache.use(1, "revision", { true }, {
            opens++
            cache.clear()
            Session().also(opened::add)
        }, { "revision" }) { assertFalse(it.closed) }
        assertTrue(opened.single().closed)
        cache.use(1, "revision", { true }, { opens++; Session() }, { "revision" }) { }
        assertEquals(2, opens)
        cache.clear()
    }

    @Test fun lockingDuringReadClosesItOnReturnWithoutClosingAnActiveHandle() = runTest {
        val session = Session()
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true })
        cache.use(1, "revision", { true }, { session }, { "revision" }) {
            cache.clear()
            assertFalse(it.closed)
        }
        assertTrue(session.closed)
    }

    @Test fun lockedReadsAndFailedReadsNeverRetainASession() = runTest {
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true })
        val locked = Session()
        cache.use(1, "revision", { false }, { locked }, { "revision" }) { }
        assertTrue(locked.closed)
        val failed = Session()
        assertTrue(runCatching {
            cache.use(1, "revision", { true }, { failed }, { "revision" }) { error("Synthetic failure") }
        }.isFailure)
        assertTrue(failed.closed)
    }

    @Test fun expiryDoesNotExtendWithActivityAndCapacityIsBounded() = runTest {
        val cache = Mdbx2ReadSessionCache<String, Session>(backgroundScope,
            { testScheduler.currentTime }, { it.closed = true }, lifetimeMillis = 1_000, capacity = 2)
        val first = Session()
        cache.use(1, "revision", { true }, { first }, { "revision" }) { }
        runCurrent()
        advanceTimeBy(800)
        cache.use(1, "revision", { true }, { error("Must reuse") }, { "revision" }) { }
        runCurrent()
        advanceTimeBy(201)
        runCurrent()
        assertTrue(first.closed)
        val remaining = (2L..4L).map { id -> Session().also { session ->
            cache.use(id, "revision", { true }, { session }, { "revision" }) { }
        } }
        assertTrue(remaining.first().closed)
        assertFalse(remaining.last().closed)
        cache.clear()
        assertTrue(remaining.all { it.closed })
    }
}
