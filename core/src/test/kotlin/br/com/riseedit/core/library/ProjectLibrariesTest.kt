package br.com.riseedit.core.library

import br.com.riseedit.core.library.ProjectLibraries.Companion.DEFAULT_ID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ProjectLibrariesTest {
    private val ids = listOf("a", "b", "c")

    @Test fun migrationPutsAllExistingProjectsInPadrao() {
        val l = ProjectLibraries().normalized(ids)
        assertEquals(listOf(LibraryFolder(DEFAULT_ID, "Padrão")), l.folders)
        ids.forEach { assertEquals(DEFAULT_ID, l.folderOf(it)) }
        assertEquals(3, l.count(DEFAULT_ID, ids))
    }

    @Test fun noProjectsNoDefaultUntilFirstProject() {
        val empty = ProjectLibraries().normalized(emptyList())
        assertTrue(empty.folders.isEmpty())
        val first = empty.assignNew("p1", null)
        assertEquals(DEFAULT_ID, first.folders.single().id)
        assertEquals(DEFAULT_ID, first.folderOf("p1"))
    }

    @Test fun newProjectGoesToSelectedLibrary() {
        val l = ProjectLibraries().normalized(ids).create("Clientes", "x").assignNew("p", "x")
        assertEquals("x", l.folderOf("p"))
    }

    @Test fun danglingAssignmentFallsBackToPadrao() {
        val l = ProjectLibraries(emptyList(), mapOf("a" to "gone")).normalized(ids)
        assertEquals(DEFAULT_ID, l.folderOf("a"))
        assertTrue(l.folders.any { it.id == DEFAULT_ID })
    }

    @Test fun renameTrimsAndIgnoresBlank() {
        val l = ProjectLibraries().normalized(ids)
        assertEquals("Meus vídeos", l.rename(DEFAULT_ID, "  Meus vídeos ").folders[0].name)
        assertEquals("Padrão", l.rename(DEFAULT_ID, "   ").folders[0].name)
    }

    @Test fun createDedupesNames() {
        val l = ProjectLibraries().create("Viagem", "1").create("viagem", "2").create("", "3")
        assertEquals(listOf("Viagem", "viagem (2)", "Nova biblioteca"), l.folders.map { it.name })
    }

    @Test fun deleteEmptyDirectlyAndNonEmptyMovesToPadrao() {
        val l = ProjectLibraries().normalized(ids).create("X", "x").create("Y", "y").move("a", "x")
        assertFalse(l.needsConfirmToDelete("y", ids))
        assertTrue(l.needsConfirmToDelete("x", ids))
        val d = l.delete("x", ids)
        assertTrue(d.folders.none { it.id == "x" })
        assertEquals(DEFAULT_ID, d.folderOf("a"))
        assertEquals(3, d.count(DEFAULT_ID, ids))
    }

    @Test fun padraoCannotBeDeleted() {
        val l = ProjectLibraries().normalized(ids)
        assertFalse(l.canDelete(DEFAULT_ID))
        assertEquals(l, l.delete(DEFAULT_ID, ids))
    }

    @Test fun moveToUnknownFolderIsIgnored() {
        val l = ProjectLibraries().normalized(ids)
        assertEquals(l, l.move("a", "nope"))
    }

    @Test fun filterScopesSearchAndFavoritesToFolder() {
        data class P(val id: String, val name: String, val fav: Boolean)
        val ps = listOf(P("a", "Praia", true), P("b", "Praia 2", false), P("c", "Show", true))
        val l = ProjectLibraries().normalized(ids).create("X", "x").move("b", "x").move("c", "x")
        fun f(folder: String?, q: String, fav: Boolean) =
            ProjectLibraries.filter(ps, l, folder, q, fav, { it.id }, { it.name }, { it.fav }).map { it.id }
        assertEquals(listOf("b", "c"), f("x", "", false))
        assertEquals(listOf("b"), f("x", "praia", false))
        assertEquals(listOf("c"), f("x", "", true))
        assertEquals(listOf("a"), f(DEFAULT_ID, "", false))
    }

    @Test fun encodeDecodeRoundTrip() {
        val l = ProjectLibraries().normalized(ids).create("X", "x").move("a", "x")
        assertEquals(l, ProjectLibraries.decode(ProjectLibraries.encode(l)))
        assertEquals(ProjectLibraries(), ProjectLibraries.decode(null))
    }
}
