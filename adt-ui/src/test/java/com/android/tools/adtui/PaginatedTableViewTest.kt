/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui

import com.android.tools.adtui.model.PaginatedListModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaginatedTableViewTest {
  @Test
  fun navigatesPages() {
    val tableView = PaginatedTableView(PaginatedListModel(2, mutableListOf(1, 2, 3)))
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()

    // Go to next page
    tableView.nextPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(1)
    assertThat(tableView.firstPageButton.isEnabled).isTrue()
    assertThat(tableView.prevPageButton.isEnabled).isTrue()
    assertThat(tableView.nextPageButton.isEnabled).isFalse()
    assertThat(tableView.lastPageButton.isEnabled).isFalse()

    // Go to previous page
    tableView.prevPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()

    // Go to last page
    tableView.nextPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(1)
    assertThat(tableView.firstPageButton.isEnabled).isTrue()
    assertThat(tableView.prevPageButton.isEnabled).isTrue()
    assertThat(tableView.nextPageButton.isEnabled).isFalse()
    assertThat(tableView.lastPageButton.isEnabled).isFalse()

    // Go to first page
    tableView.prevPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()
  }

  @Test
  fun sortsAllPages() {
    val tableView = PaginatedTableView(PaginatedListModel(2, mutableListOf(1, 2, 3)))
    tableView.table.rowSorter.toggleSortOrder(0)
    assertThat(tableView.table.getValueAt(0, 0)).isEqualTo(1)
    assertThat(tableView.table.getValueAt(1, 0)).isEqualTo(2)
    tableView.table.rowSorter.toggleSortOrder(0)
    assertThat(tableView.table.getValueAt(0, 0)).isEqualTo(3)
    assertThat(tableView.table.getValueAt(1, 0)).isEqualTo(2)
  }
}