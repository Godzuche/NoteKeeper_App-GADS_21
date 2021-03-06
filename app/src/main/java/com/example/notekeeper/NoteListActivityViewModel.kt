package com.example.notekeeper

import android.os.Bundle
import androidx.lifecycle.ViewModel

class NoteListActivityViewModel : ViewModel() {
    var isNewlyCreated = true
    val navDrawerDisplaySelectionName =
        "com.example.notekeeper.NoteListActivityViewModel.navDrawerDisplaySelection"
    val recentlyViewedNoteIdsName =
        "com.example.notekeeper.NoteListActivityViewModel.recentlyViewedNoteIds"

    var navDrawerDisplaySelection = R.id.nav_notes

    private val maxRecentlyViewedNotes = 5
    val recentlyViewedNotes = ArrayList<NoteInfo>(maxRecentlyViewedNotes)

    fun addToRecentlyViewedNotes(note: NoteInfo) {
        //check if selection is already in the list
        val existingIndex = recentlyViewedNotes.indexOf(note)
        if (existingIndex == -1) {
            //it isn't in the list...
            //Add new one to beginning of the list and remove any beyond max we want to keep
            recentlyViewedNotes.add(0, note)
            for (index in recentlyViewedNotes.lastIndex downTo maxRecentlyViewedNotes) {
                recentlyViewedNotes.removeAt(index)
            }
        } else {
            //it is in the list...
            //shift the ones above down the list and make it the first member of the list
            for (index in (existingIndex - 1) downTo 0) {
                recentlyViewedNotes[index + 1] = recentlyViewedNotes[index]
            }
            recentlyViewedNotes[0] = note
        }
    }

    fun saveState(outState: Bundle) {
        outState.putInt(navDrawerDisplaySelectionName, navDrawerDisplaySelection)
        val noteIds = DataManager.noteIdsAsIntArray(recentlyViewedNotes)
        outState.putIntArray(recentlyViewedNoteIdsName, noteIds)
    }

    fun restoreState(savedInstanceState: Bundle) {
        navDrawerDisplaySelection = savedInstanceState.getInt(navDrawerDisplaySelectionName)
        val noteIds = savedInstanceState.getIntArray(recentlyViewedNoteIdsName)
        val noteList = noteIds?.let { DataManager.loadNotes(*noteIds) }
        if (noteList != null) {
            recentlyViewedNotes.addAll(noteList)
        }
    }
}