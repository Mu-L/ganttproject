/*
Copyright 2017-2026 Oleg Kushnikov, Dmitry Barashev, BarD Software s.r.o

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
package net.sourceforge.ganttproject.gui.resourceproperties

import biz.ganttproject.app.FXThread
import biz.ganttproject.app.RootLocalizer
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.util.Callback
import javafx.util.converter.DefaultStringConverter
import net.sourceforge.ganttproject.resource.HumanResource
import net.sourceforge.ganttproject.task.ResourceAssignment
import net.sourceforge.ganttproject.task.ResourceAssignmentMutator
import net.sourceforge.ganttproject.task.Task
import net.sourceforge.ganttproject.task.TaskManager
import org.controlsfx.control.tableview2.TableColumn2
import org.controlsfx.control.tableview2.TableView2

/**
 * UI component in a resource properties dialog: a table with tasks assigned to
 * a resource.
 */
class ResourceAssignmentsPanelFx(
  private val person: HumanResource,
  private val taskManager: TaskManager
) {
  private val tableItems: ObservableList<ResourceAssignmentRow> = FXCollections.observableArrayList()
  private val tableView = TableView2<ResourceAssignmentRow>()
  private val model = object : net.sourceforge.ganttproject.gui.TableView2TableActionsModel<ResourceAssignmentRow>(tableView) {
    private val innerModel = ResourceAssignmentTableModelFx(person)

    override fun delete(indices: IntArray) {
      innerModel.delete(indices)
      refreshTable()
    }

    override fun onAdd() {
      FXThread.runLater {
        tableView.edit(tableItems.size - 1, tableView.columns[1])
        tableView.selectionModel.select(tableItems.size - 1)
      }
    }

    override fun refreshTable() {
      val currentSelection = tableView.selectionModel.selectedIndex
      tableItems.clear()
      val assignments = innerModel.assignments
      assignments.forEach { assignment ->
        tableItems.add(ResourceAssignmentRow(assignment))
      }
      // The last row is for adding new assignments
      tableItems.add(ResourceAssignmentRow(null))
      if (currentSelection != -1 && currentSelection < tableItems.size) {
        tableView.selectionModel.select(currentSelection)
      }
    }

    fun commit() {
      innerModel.commit()
    }

    fun setValueAt(value: Any?, row: Int, col: Int) {
      innerModel.setValueAt(value, row, col)
    }
  }

  private val tableAndActions =
    _root_ide_package_.net.sourceforge.ganttproject.gui.AbstractTableAndActionsComponentFx(tableView, model)

  val fxComponent: Region by lazy {
    getFxNode().also {
      model.refreshTable()
    }
  }

  private fun getFxNode(): Region {
    // TODO: restore column widths
    tableView.apply {
      isEditable = true
      items = tableItems
      selectionModel.selectionMode = SelectionMode.SINGLE

      // ID column
      val idCol = TableColumn2<ResourceAssignmentRow, String>(i18n.formatText("id")).apply {
        setCellValueFactory { SimpleStringProperty(it.value.assignment?.task?.taskID?.toString() ?: "") }
        prefWidth = 50.0
        isEditable = false
      }

      // Task name column with combo box editor
      val nameCol = TableColumn2<ResourceAssignmentRow, Task?>(i18n.formatText("taskname")).apply {
        setCellValueFactory { SimpleObjectProperty(it.value.assignment?.task) }
        setCellFactory { TaskComboTableCell(taskManager) }
        setOnEditCommit { event ->
          val row = event.rowValue
          val newTask = event.newValue
          if (row.assignment == null) {
            if (newTask != null) {
              model.setValueAt(newTask, tableItems.size - 1, 1)
              model.refreshTable()
            }
          } else {
            // In the original ResourceAssignmentsTableModel, Column.NAME is only editable for the new row.
            // But if we want to allow changing task, we can do it here. 
            // However, looking at original isCellEditable:
            // if (row == myAssignments.size()) { return Column.NAME.equals(Column.values()[col]); }
            // So for existing assignments, NAME is NOT editable.
          }
        }
        isEditable = true
        prefWidth = 300.0
      }

      // Unit column
      val unitCol = TableColumn2<ResourceAssignmentRow, String>(i18n.formatText("unit")).apply {
        setCellValueFactory { SimpleStringProperty(it.value.assignment?.load?.toString() ?: "") }
        setCellFactory { TextFieldTableCell(DefaultStringConverter()) }
        setOnEditCommit { event ->
          try {
            val load = event.newValue.toFloat()
            model.setValueAt(load, event.tablePosition.row, 2)
          } catch (e: NumberFormatException) {
          }
          model.refreshTable()
        }
        isEditable = true
        prefWidth = 80.0
      }

      columns.addAll(idCol, nameCol, unitCol)
    }

    return tableAndActions.fxComponent.also {
      it.stylesheets.addAll(
        "/biz/ganttproject/task/TaskPropertiesDialog.css",
        "/biz/ganttproject/app/tables.css",
        "/biz/ganttproject/app/buttons.css"
      )
      it.styleClass.addAll("tab-contents", "pane-resource-assignments")
    }
  }

  fun commit() {
    model.commit()
  }

  fun requestFocus() {
    tableAndActions.requestFocus()
  }
}

// --------------------------------------------------------------------------------------------------------------------
private val i18n = RootLocalizer

/**
 * Table model for resource assignments (internal for ResourceAssignmentsPanelFx).
 */
private class ResourceAssignmentTableModelFx(private val person: HumanResource) {
  private val _assignments = person.assignments.toMutableList()
  private val assignmentsToDelete = mutableListOf<ResourceAssignment>()
  private val task2MutatorMap = mutableMapOf<Task, ResourceAssignmentMutator>()

  val assignments: List<ResourceAssignment>
    get() = _assignments.toList()

  fun setValueAt(value: Any?, row: Int, col: Int) {
    if (value == null) return
    if (row >= _assignments.size) {
      createAssignment(value)
    } else {
      updateAssignment(value, row, col)
    }
  }

  private fun updateAssignment(value: Any?, row: Int, col: Int) {
    val assignment = _assignments[row]
    if (col == 2) { // UNIT
      if (value is Float) {
        assignment.load = value
      }
    }
  }

  private fun createAssignment(value: Any?) {
    if (value is Task) {
      val mutator = getMutator(value)
      val ra = mutator.addAssignment(person)
      ra.load = 100f
      _assignments.add(ra)
    }
  }

  private fun getMutator(task: Task): ResourceAssignmentMutator {
    return task2MutatorMap.getOrPut(task) {
      task.assignmentCollection.createMutator()
    }
  }

  fun delete(selectedRows: IntArray) {
    val toDelete = selectedRows.filter { it < _assignments.size }.map { _assignments[it] }
    for (ra in toDelete) {
      val mutator = getMutator(ra.task)
      mutator.deleteAssignment(person)
      assignmentsToDelete.add(ra)
    }
    _assignments.removeAll(toDelete)
  }

  fun commit() {
    task2MutatorMap.values.forEach { it.commit() }
    assignmentsToDelete.forEach { ra ->
      if (!_assignments.contains(ra)) {
        ra.delete()
      }
    }
  }
}

/**
 * Row data class for the table.
 */
private class ResourceAssignmentRow(val assignment: ResourceAssignment?)

/**
 * Custom table cell for task selection with combo box.
 */
private class TaskComboTableCell(taskManager: TaskManager) : javafx.scene.control.TableCell<ResourceAssignmentRow, Task?>() {
  private val comboBox = ComboBox<Task>()
  private val allTasks = taskManager.tasks.toList()

  init {
    comboBox.items.addAll(allTasks)
    comboBox.cellFactory = Callback { TaskListCell() }
    comboBox.buttonCell = TaskListCell()
    comboBox.maxWidth = Double.MAX_VALUE
    comboBox.setOnAction {
      if (isEditing) {
        commitEdit(comboBox.value)
      }
    }
  }

  override fun startEdit() {
    val row = tableView.items[index]
    if (row.assignment != null) {
      // Original logic: NAME is not editable for existing assignments
      return
    }
    super.startEdit()
    comboBox.value = item
    graphic = comboBox
    text = null
  }

  override fun cancelEdit() {
    super.cancelEdit()
    graphic = null
    text = item?.name
  }

  override fun updateItem(item: Task?, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty || item == null) {
      text = null
      graphic = null
    } else {
      if (isEditing) {
        graphic = comboBox
        text = null
      } else {
        text = item.name
        graphic = null
      }
    }
  }
}

/**
 * List cell for displaying tasks in combo box.
 */
private class TaskListCell : javafx.scene.control.ListCell<Task>() {
  override fun updateItem(item: Task?, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty || item == null) {
      text = null
      graphic = null
    } else {
      val hBox = HBox(10.0).also {
        it.styleClass.add("task-cell")
      }
      val idLabel = Label(item.taskID.toString()).apply {
        prefWidth = 40.0
        alignment = Pos.CENTER_RIGHT
        style = "-fx-font-size: 90%;"
      }
      val nameLabel = Label(item.name).apply {
        val depth = item.manager.taskHierarchy.getDepth(item)
        padding = Insets(0.0, 0.0, 0.0, (depth - 1) * 10.0)
      }
      hBox.children.addAll(idLabel, nameLabel)
      graphic = hBox
      text = null
    }
  }
}
