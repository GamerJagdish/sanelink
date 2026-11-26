package com.sanelink.app.ui.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** Saves an editable list of strings across rotation and process death. */
val StringStateListSaver: Saver<SnapshotStateList<String>, Any> = listSaver(
    save = { ArrayList(it) },
    restore = { it.toMutableStateList() }
)
