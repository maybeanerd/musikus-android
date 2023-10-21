/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2022 Matthias Emde
 */

package app.musikus.repository

import app.musikus.database.PTDatabase
import app.musikus.database.entities.LibraryFolder
import app.musikus.database.entities.LibraryItem
import app.musikus.database.entities.LibraryItemCreationAttributes
import app.musikus.database.entities.LibraryItemUpdateAttributes
import app.musikus.datastore.LibraryFolderSortMode
import app.musikus.datastore.LibraryItemSortMode
import app.musikus.datastore.SortDirection

class LibraryRepository(
    database: PTDatabase
) {
    private val itemDao = database.libraryItemDao
    private val folderDao = database.libraryFolderDao

    val items = itemDao.getAllAsFlow()
    val folders = folderDao.getAllAsFlow()


    /** Mutators */
    /** Add */
    suspend fun addFolder(newFolder: LibraryFolder) {
        folderDao.insert(newFolder)
    }

    suspend fun addItem(creationAttributes: LibraryItemCreationAttributes) {
        itemDao.insert(LibraryItem(creationAttributes))
    }

    /** Edit */
    suspend fun editFolder(
        folder: LibraryFolder,
        newName: String,
    ) {
        folder.apply {
            name = newName
        }
//        folderDao.update(folder)
    }

    suspend fun editItem(updateAttributes: LibraryItemUpdateAttributes) {
        itemDao.update(LibraryItem(updateAttributes))
    }

    /** Delete / restore */

    suspend fun deleteItems(items: List<LibraryItem>) {
        itemDao.delete(items.map { it.id })
    }

    suspend fun restoreItems(items: List<LibraryItem>) {
        itemDao.restore(items.map{ it.id })
    }

    suspend fun deleteFolders(folders: List<LibraryFolder>) {
        folderDao.delete(folders.map { it.id })
    }

    suspend fun restoreFolders(folders: List<LibraryFolder>) {
        folderDao.restore(folders.map { it.id })
    }

    /** Clean */

    suspend fun clean() {
        folderDao.clean()
        itemDao.clean()
    }

    /** Sort */
    fun sortFolders(
        folders: List<LibraryFolder>,
        mode: LibraryFolderSortMode,
        direction: SortDirection,
    ) = when (direction) {
        SortDirection.ASCENDING -> {
            when (mode) {
                LibraryFolderSortMode.DATE_ADDED -> folders.sortedBy { it.createdAt }
                LibraryFolderSortMode.LAST_MODIFIED -> folders.sortedBy { it.modifiedAt }
                LibraryFolderSortMode.NAME -> folders.sortedBy { it.name }
                LibraryFolderSortMode.CUSTOM -> folders // TODO
            }
        }
        SortDirection.DESCENDING -> {
            when (mode) {
                LibraryFolderSortMode.DATE_ADDED -> folders.sortedByDescending { it.createdAt }
                LibraryFolderSortMode.LAST_MODIFIED -> folders.sortedByDescending { it.modifiedAt }
                LibraryFolderSortMode.NAME -> folders.sortedByDescending { it.name }
                LibraryFolderSortMode.CUSTOM -> folders // TODO
            }
        }
    }

    fun sortItems(
        items: List<LibraryItem>,
        mode: LibraryItemSortMode,
        direction: SortDirection,
    ) = when (direction) {
        SortDirection.ASCENDING -> {
            when (mode) {
                LibraryItemSortMode.DATE_ADDED -> items.sortedBy { it.createdAt }
                LibraryItemSortMode.LAST_MODIFIED -> items.sortedBy { it.modifiedAt }
                LibraryItemSortMode.NAME -> items.sortedBy { it.name }
                LibraryItemSortMode.COLOR -> items.sortedBy { it.colorIndex }
                LibraryItemSortMode.CUSTOM -> items // TODO
            }
        }
        SortDirection.DESCENDING -> {
            when (mode) {
                LibraryItemSortMode.DATE_ADDED -> items.sortedByDescending { it.createdAt }
                LibraryItemSortMode.LAST_MODIFIED -> items.sortedByDescending { it.modifiedAt }
                LibraryItemSortMode.NAME -> items.sortedByDescending { it.name }
                LibraryItemSortMode.COLOR -> items.sortedByDescending { it.colorIndex }
                LibraryItemSortMode.CUSTOM -> items // TODO
            }
        }
    }
}