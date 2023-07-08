/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.AnkiAssert.assertEqualsArrayList
import org.apache.http.util.Asserts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DecksTest : RobolectricTest() {
    @Test
    @Suppress("SpellCheckingInspection")
    fun ensureDeckList() {
        val decks = col.decks
        for (deckName in TEST_DECKS) {
            addDeck(deckName)
        }
        val brokenDeck = decks.byName("cmxieunwoogyxsctnjmv::INSBGDS")
        Asserts.notNull(brokenDeck, "We should get deck with given name")
        // Changing the case. That could exists in an old collection or during sync.
        brokenDeck!!.put("name", "CMXIEUNWOOGYXSCTNJMV::INSBGDS")
        decks.save(brokenDeck)

        decks.childMap()
        for (deck in decks.all()) {
            val did = deck.getLong("id")
            for (parent in decks.parents(did)) {
                Asserts.notNull(parent, "Parent should not be null")
            }
        }
    }

    @Test
    fun trim() {
        assertThat(Decks.strip("A\nB C\t D"), equalTo("A\nB C\t D"))
        assertThat(Decks.strip("\n A\n\t"), equalTo("A"))
        assertThat(Decks.strip("Z::\n A\n\t::Y"), equalTo("Z::A::Y"))
    }

    /******************
     * autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     */
    @Test
    fun test_basic() {
        val col = col
        val decks = col.decks
        // we start with a standard col
        assertEquals(1, decks.allSortedNames().size.toLong())
        // it should have an id of 1
        assertNotNull(decks.name(1))
        // create a new col
        val parentId = addDeck("new deck")
        assertNotEquals(parentId, 0)
        assertEquals(2, decks.allSortedNames().size.toLong())
        // should get the same id
        assertEquals(parentId, addDeck("new deck"))
        // we start with the default col selected
        assertEquals(1, decks.selected())
        assertEqualsArrayList(arrayOf(1L), decks.active())
        // we can select a different col
        decks.select(parentId)
        assertEquals(parentId, decks.selected())
        assertEqualsArrayList(arrayOf(parentId), decks.active())
        // let's create a child
        val childId = addDeck("new deck::child")
        col.reset()
        // it should have been added to the active list
        assertEquals(parentId, decks.selected())
        assertEqualsArrayList(arrayOf(parentId, childId), decks.active())
        // we can select the child individually too
        decks.select(childId)
        assertEquals(childId, decks.selected())
        assertEqualsArrayList(arrayOf(childId), decks.active())
        // parents with a different case should be handled correctly
        addDeck("ONE")
        val m = col.models.current()
        m!!.put("did", addDeck("one::two"))
        col.models.save(m, false)
        val n = col.newNote()
        n.setItem("Front", "abc")
        col.addNote(n)

        assertEquals(decks.id_for_name("new deck")!!.toLong(), parentId)
        assertEquals(decks.id_for_name("  New Deck  ")!!.toLong(), parentId)
        assertNull(decks.id_for_name("Not existing deck"))
        assertNull(decks.id_for_name("new deck::not either"))
    }

    @Test
    fun test_remove() {
        val col = col
        // create a new col, and add a note/card to it
        val deck1 = addDeck("deck1")
        val note = col.newNote()
        note.setItem("Front", "1")
        note.model().put("did", deck1)
        col.addNote(note)
        val c = note.cards()[0]
        assertEquals(deck1, c.did)
        assertEquals(1, col.cardCount().toLong())
        col.decks.rem(deck1)
        assertEquals(0, col.cardCount().toLong())
        // if we try to get it, we get the default
        assertEquals("[no deck]", col.decks.name(c.did))
    }

    @Test
    @SuppressLint("CheckResult")
    @Throws(DeckRenameException::class)
    fun test_rename() {
        val col = col
        var id = addDeck("hello::world")
        // should be able to rename into a completely different branch, creating
        // parents as necessary
        val decks = col.decks
        decks.rename(decks.get(id), "foo::bar")
        var names: List<String?> = decks.allSortedNames()
        assertTrue(names.contains("foo"))
        assertTrue(names.contains("foo::bar"))
        assertFalse(names.contains("hello::world"))
        // create another col
        /* TODO: do we want to follow upstream here ?
         // automatically adjusted if a duplicate name
         decks.rename(decks.get(id), "FOO");
         names =  decks.allSortedNames();
         assertThat(names, containsString("FOO+"));

          */
        // when renaming, the children should be renamed too
        addDeck("one::two::three")
        id = addDeck("one")
        col.decks.rename(col.decks.get(id), "yo")
        names = col.decks.allSortedNames()
        for (n in arrayOf("yo", "yo::two", "yo::two::three")) {
            assertTrue(names.contains(n))
        }
        // over filtered
        val filteredId = addDynamicDeck("filtered")
        col.decks.get(filteredId)
        val childId = addDeck("child")
        val child = col.decks.get(childId)
        assertThrows(DeckRenameException::class.java) {
            col.decks.rename(
                child,
                "filtered::child"
            )
        }
        assertThrows(DeckRenameException::class.java) {
            col.decks.rename(
                child,
                "FILTERED::child"
            )
        }
    }

    /* TODO: maybe implement. We don't drag and drop here anyway, so buggy implementation is okay
     @Test public void test_renameForDragAndDrop() throws DeckRenameException {
     // TODO: upstream does not return "default", remove it
     Collection col = getCol();

     long languages_did = addDeck("Languages");
     long chinese_did = addDeck("Chinese");
     long hsk_did = addDeck("Chinese::HSK");

     // Renaming also renames children
     col.getDecks().renameForDragAndDrop(chinese_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto itself is a no-op
     col.getDecks().renameForDragAndDrop(languages_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto its parent is a no-op
     col.getDecks().renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto a descendant is a no-op
     col.getDecks().renameForDragAndDrop(languages_did, hsk_did);
     // TODO: real problem to correct, even if we don't have drag and drop
     // assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Can drag a grandchild onto its grandparent.  It becomes a child
     col.getDecks().renameForDragAndDrop(hsk_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::HSK"}, col.getDecks().allSortedNames());

     // Can drag a col onto its sibling
     col.getDecks().renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Can drag a col back to the top level
     col.getDecks().renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, col.getDecks().allSortedNames());

     // Dragging a top level col to the top level is a no-op
     col.getDecks().renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, col.getDecks().allSortedNames());

     // decks are renamed if necessary«
     long new_hsk_did = addDeck("hsk");
     col.getDecks().renameForDragAndDrop(new_hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Chinese::hsk+", "Languages"}, col.getDecks().allSortedNames());
     col.getDecks().rem(new_hsk_did);

     }
     */
    @Test
    fun curDeckIsLong() {
        // Regression for #8092
        val col = col
        val decks = col.decks
        val id = addDeck("test")
        decks.select(id)
        assertDoesNotThrow("curDeck should be saved as a long. A deck id.") {
            col.get_config_long(
                CURRENT_DECK
            )
        }
    }

    @Test
    fun isDynStd() {
        val col = col
        val decks = col.decks
        val filteredId = addDynamicDeck("filtered")
        val filtered = decks.get(filteredId)
        val deckId = addDeck("deck")
        val deck = decks.get(deckId)
        assertThat(deck.isStd, equalTo(true))
        assertThat(deck.isDyn, equalTo(false))
        assertThat(filtered.isStd, equalTo(false))
        assertThat(filtered.isDyn, equalTo(true))

        val filteredConfig = decks.confForDid(filteredId)
        val deckConfig = decks.confForDid(deckId)
        assertThat(deckConfig.isStd, equalTo((true)))
        assertThat(deckConfig.isDyn, equalTo((false)))
        assertThat(filteredConfig.isStd, equalTo((false)))
        assertThat(filteredConfig.isDyn, equalTo((true)))
    }

    @Test
    fun confForDidReturnsDefaultIfNotFound() {
        // https://github.com/ankitects/anki/commit/94d369db18c2a6ac3b0614498d8abcc7db538633
        val decks = col.decks

        val d = decks.all()[0]
        d.put("conf", 12L)
        decks.save()

        val config = decks.confForDid(d.getLong("id"))
        assertThat(
            "If a config is not found, return the default",
            config.getLong("id"),
            equalTo(1L)
        )
    }

    companion object {
        // Used in other class to populate decks.
        @Suppress("SpellCheckingInspection")
        val TEST_DECKS = arrayOf(
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS",
            "::foobar", // Addition test for issue #11026
            "A::"
        )
    }
}
