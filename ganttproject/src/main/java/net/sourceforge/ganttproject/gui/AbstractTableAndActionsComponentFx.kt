/*
Copyright 2003-2026 Dmitry Barashev, BarD Software s.r.o.

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.ganttproject.gui

import biz.ganttproject.app.FXThread
import biz.ganttproject.createButton
import biz.ganttproject.lib.fx.vbox
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import net.sourceforge.ganttproject.action.GPAction
import org.controlsfx.control.tableview2.TableView2

/**
 * Interface for the table actions model, replacing TableModelExt.
 */
interface TableActionsModel<T> {
  /**
   * Returns all items currently in the table.
   */
  fun getAllItems(): List<T>

  /**
   * Deletes the items at the specified row indices.
   */
  fun delete(indices: IntArray)

  /**
   * Called when the "Add" action is triggered.
   */
  fun onAdd()

  /**
   * Refreshes the table.
   */
  fun refreshTable()

  val selectedIndices: ObservableList<Int>
  val selectedItems: ObservableList<T>
  fun select(index: Int)
  fun clearSelection()
  fun scrollTo(index: Int)
  fun requestFocus()
  var isDisable: Boolean
  val isDisabled: Boolean
  val itemCount: Int
}

/**
 * A JavaFX/Kotlin implementation of a component consisting of a table and a set of actions.
 */
class AbstractTableAndActionsComponentFx<T>(
  private val tableRegion: Region,
  private val model: TableActionsModel<T>
) {
  val fxComponent by lazy {
    createDefaultLayout().also {
      refreshTable()
    }
  }

  private val additionalActions = mutableListOf<GPAction>()
  private val listeners = mutableListOf<SelectionListener<T>>()

  private val actionAddRow = GPAction.create("add") {
    FXThread.runLater { model.onAdd() }
  }.also {
    it.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY)
  }

  private val actionDeleteRow = GPAction.create("delete") {
    FXThread.runLater {
      val selectedIndices = model.selectedIndices
      if (selectedIndices.isNotEmpty()) {
        model.delete(selectedIndices.toIntArray())
        refreshTable()
      }
    }
  }.also {
    it.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY)
  }

  init {
    model.selectedIndices.addListener(ListChangeListener {
      updateActions()
      fireSelectionChanged(getSelectedItems())
    })
    addAction(actionAddRow)
    addAction(actionDeleteRow)
    updateActions()
  }

  fun addAction(action: GPAction) {
    if (action.getValue(AbstractTableAndActionsComponent.PROPERTY_IS_ENABLED_FUNCTION) == null) {
      action.isEnabled = true
    }
    additionalActions.add(action)
    if (action is SelectionListener<*>) {
      @Suppress("UNCHECKED_CAST")
      addSelectionListener(action as SelectionListener<T>)
    }
  }

  private fun updateActions() {
    val selectedIndices = model.selectedIndices
    val isTableEnabled = !model.isDisabled
    actionAddRow.isEnabled = isTableEnabled
    actionDeleteRow.isEnabled = isTableEnabled && selectedIndices.isNotEmpty() && !selectedIndices.contains(model.itemCount - 1)

    val selectedItems = getSelectedItems()
    additionalActions.forEach { action ->
      val isEnabledFunc = action.getValue(AbstractTableAndActionsComponent.PROPERTY_IS_ENABLED_FUNCTION)
      if (isEnabledFunc is com.google.common.base.Function<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val func = isEnabledFunc as com.google.common.base.Function<List<T>, Boolean>
        action.isEnabled = func.apply(selectedItems) == true
      }
    }
  }

  private fun fireSelectionChanged(selectedObjects: List<T>) {
    listeners.forEach { it.selectionChanged(selectedObjects) }
  }

  fun addSelectionListener(listener: SelectionListener<T>) {
    listeners.add(listener)
  }

  fun setSelection(index: Int) {
    if (index == -1) {
      model.clearSelection()
    } else {
      model.select(index)
      model.scrollTo(index)
    }
  }

  fun setEnabled(enabled: Boolean) {
    model.isDisable = !enabled
    updateActions()
  }

  fun requestFocus() {
    if (model.itemCount > 0) {
      model.select(0)
    }
    model.requestFocus()
  }

  private fun getSelectedItems(): List<T> {
    return model.selectedItems.filterNotNull()
  }

  fun getActionsComponent(): Region {
    return HBox(5.0).apply {
      additionalActions.forEach { action ->
        children.add(createButton(action, onlyIcon = false).apply {
          styleClass.addAll("btn", "btn-regular", "secondary", "small")
        })
      }
    }
  }

  /**
   * Creates a default layout with actions at the top and the table in the center.
   */
  fun createDefaultLayout(): Region {
    return BorderPane().apply {
      top = vbox {
        add(getActionsComponent())
        add(HBox().also { it.styleClass.add("medskip") })
        center = tableRegion
      }
    }
  }

  fun refreshTable() {
    model.refreshTable()
  }

  interface SelectionListener<T> {
    fun selectionChanged(selection: List<T>)
  }
}

/**
 * An implementation of TableActionsModel that delegates to TableView2.
 */
abstract class TableView2TableActionsModel<T>(protected val tableView: TableView2<T>) : TableActionsModel<T> {
  override val selectedIndices: ObservableList<Int> get() = tableView.selectionModel.selectedIndices
  override val selectedItems: ObservableList<T> get() = tableView.selectionModel.selectedItems
  override fun select(index: Int) = tableView.selectionModel.select(index)
  override fun clearSelection() = tableView.selectionModel.clearSelection()
  override fun scrollTo(index: Int) = tableView.scrollTo(index)
  override fun requestFocus() { tableView.requestFocus() }
  override var isDisable: Boolean
    get() = tableView.isDisable
    set(value) { tableView.isDisable = value }
  override val isDisabled: Boolean get() = tableView.isDisabled
  override val itemCount: Int get() = tableView.items.size
  override fun getAllItems(): List<T> = tableView.items.toList()
}
