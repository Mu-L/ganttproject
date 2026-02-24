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
package net.sourceforge.ganttproject.gui.taskproperties

import biz.ganttproject.app.FXThread
import biz.ganttproject.app.RootLocalizer
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.ChoiceBoxTableCell
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.util.Callback
import javafx.util.StringConverter
import javafx.util.converter.DefaultStringConverter
import net.sourceforge.ganttproject.gui.AbstractTableAndActionsComponentFx
import net.sourceforge.ganttproject.gui.TableActionsModel
import net.sourceforge.ganttproject.gui.TableView2TableActionsModel
import net.sourceforge.ganttproject.task.Task
import net.sourceforge.ganttproject.task.dependency.TaskDependency
import net.sourceforge.ganttproject.task.dependency.TaskDependencyConstraint
import net.sourceforge.ganttproject.task.dependency.constraint.FinishFinishConstraintImpl
import net.sourceforge.ganttproject.task.dependency.constraint.FinishStartConstraintImpl
import net.sourceforge.ganttproject.task.dependency.constraint.StartFinishConstraintImpl
import net.sourceforge.ganttproject.task.dependency.constraint.StartStartConstraintImpl
import org.controlsfx.control.tableview2.TableColumn2
import org.controlsfx.control.tableview2.TableView2

/**
 * This class represents a JavaFX panel for managing task dependencies.
 */
class TaskDependenciesPanelFx(private val task: Task) {
  private val tableItems: ObservableList<DependencyRow> = FXCollections.observableArrayList()
  private val tableView = TableView2<DependencyRow>()
  private val model = object : TableView2TableActionsModel<DependencyRow>(tableView) {
    private val innerModel = DependencyTableModel(task)

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
      val currentSelection = selectedRow
      tableItems.clear()
      // The last row is for adding new dependencies
      val dependencies = innerModel.dependencies
      dependencies.forEach { dep ->
        tableItems.add(DependencyRow(dep))
      }
      tableItems.add(DependencyRow(null))
      selectedRow = currentSelection
    }

    fun commit() {
      innerModel.commit()
    }

    fun setValueAt(value: Any?, row: Int, col: Int) {
      innerModel.setValueAt(value, row, col)
    }
  }

  val title: String = i18n.formatText("predecessors")
  private val tableAndActions = AbstractTableAndActionsComponentFx(tableView, model)

  val fxComponent by lazy {
    getFxNode().also {
      model.refreshTable()
    }
  }

  private var selectedIndices = FXCollections.emptyObservableList<Int>()
  private var selectedRow: Int
    set(value) {
      selectRow(value)
    }
    get() = selectedIndices.firstOrNull() ?: -1
  private var selectRow: (Int) -> Unit = {}

  private fun getFxNode(): Region  {
    tableView.apply {
      isEditable = true
      items = tableItems
      selectionModel.selectionMode = SelectionMode.SINGLE
      selectedIndices = selectionModel.selectedIndices
      selectRow = { selectionModel.select(it) }

      // This column shows the predecessor task ID
      val idCol = TableColumn2<DependencyRow, Number>(i18n.formatText("id")).apply {
        setCellValueFactory { SimpleIntegerProperty(it.value.dependency?.dependee?.taskID ?: 0) }
        prefWidth = 50.0
        isEditable = false
      }

      // This column displays the predecessor task name. It is editable. When editing, it uses a dropdown editor
      // with the element consisting of task ID and name.
      val nameCol = TableColumn2<DependencyRow, DependencyTableModel.TaskComboItem>(i18n.formatText("taskname")).apply {
        setCellValueFactory { SimpleObjectProperty(it.value.taskItem) }
        setCellFactory { TaskComboTableCell(task) }
        setOnEditCommit { event ->
          val row = event.rowValue
          val newItem = event.newValue
          if (row.dependency == null) {
            if (newItem != null) {
              model.setValueAt(newItem, tableItems.size - 1, 1)
              model.refreshTable()
            }
          } else {
            model.setValueAt(newItem, event.tablePosition.row, 1)
            model.refreshTable()
          }
        }
        isEditable = true
        prefWidth = 250.0
      }

      val typeStringConverter = object : StringConverter<TaskDependencyConstraint>() {
        override fun toString(obj: TaskDependencyConstraint?): String = obj?.name ?: ""
        override fun fromString(value: String?): TaskDependencyConstraint? {
          return constraints.find { it.name == value }
        }
      }
      // This column shows a dependency constraint. It is editable, with a dropdown editor for constraint selection.
      val typeCol = TableColumn2<DependencyRow, TaskDependencyConstraint>(i18n.formatText("type")).apply {
        setCellValueFactory { SimpleObjectProperty(it.value.dependency?.constraint) }
        cellFactory = ChoiceBoxTableCell.forTableColumn(
          typeStringConverter,
          FXCollections.observableArrayList(constraints)
        )
        setOnEditCommit { event ->
          model.setValueAt(event.newValue, event.tablePosition.row, 2)
          model.refreshTable()
        }
        isEditable = true
        prefWidth = 150.0
      }

      // This column shows a dependency lag. It is editable, with a text field editor for lag value input.
      val lagCol = TableColumn2<DependencyRow, String>(i18n.formatText("delay")).apply {
        setCellValueFactory { SimpleStringProperty(it.value.dependency?.difference?.toString() ?: "") }
        // Simple text edit for lag
        setCellFactory { TextFieldTableCell(DefaultStringConverter()) }
        setOnEditCommit { event ->
          try {
            model.setValueAt(event.newValue, event.tablePosition.row, 3)
          } catch (e: NumberFormatException) {
          }
          model.refreshTable()
        }
        isEditable = true
        prefWidth = 80.0
      }

      // This column shows a dependency hardness. It is editable, with a dropdown editor for hardness selection.
      val hardnessCol = TableColumn2<DependencyRow, TaskDependency.Hardness>(i18n.formatText("hardness")).apply {
        setCellValueFactory { SimpleObjectProperty(it.value.dependency?.hardness) }
        cellFactory = ChoiceBoxTableCell.forTableColumn(
          TaskDependency.Hardness.RUBBER,
          TaskDependency.Hardness.STRONG
        )
        setOnEditCommit { event ->
          model.setValueAt(event.newValue, event.tablePosition.row, 4)
          model.refreshTable()
        }
        isEditable = true
        prefWidth = 100.0
      }

      columns.addAll(idCol, nameCol, typeCol, lagCol, hardnessCol)
    }
    return tableAndActions.fxComponent.also {
      it.stylesheets.addAll(
        "/biz/ganttproject/task/TaskPropertiesDialog.css",
        "/biz/ganttproject/app/tables.css",
        "/biz/ganttproject/app/buttons.css"
      )
      it.styleClass.addAll("tab-contents", "pane-task-dependencies")
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
private val constraints = listOf<TaskDependencyConstraint>(
  FinishStartConstraintImpl(), FinishFinishConstraintImpl(),
  StartFinishConstraintImpl(), StartStartConstraintImpl()
)
private val i18n = RootLocalizer

// --------------------------------------------------------------------------------------------------------------------

/**
 * This is a table model object,
 */
private class DependencyRow(val dependency: TaskDependency?) {
  val taskItem: DependencyTableModel.TaskComboItem? =
    dependency?.dependee?.let { DependencyTableModel.TaskComboItem(it) }
}

/**
 * This class represents a table cell for displaying and editing task dependencies in a combo box.
 * It provides a dropdown editor for selecting predecessor tasks.
 */
private class TaskComboTableCell(task: Task) : TableCell<DependencyRow, DependencyTableModel.TaskComboItem>() {
  private val comboBox = ComboBox<DependencyTableModel.TaskComboItem>()
  private val predecessorCandidates = task.manager.algorithmCollection.findPossibleDependeesAlgorithm.run(task)

  init {
    comboBox.items.addAll(predecessorCandidates.map { DependencyTableModel.TaskComboItem(it) })
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
    super.startEdit()
    comboBox.value = item
    graphic = comboBox
    text = null
  }

  override fun cancelEdit() {
    super.cancelEdit()
    graphic = null
    text = item?.toString()
  }

  override fun updateItem(item: DependencyTableModel.TaskComboItem?, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty || item == null) {
      text = null
      graphic = null
    } else {
      if (isEditing) {
        graphic = comboBox
        text = null
      } else {
        text = item.toString()
        graphic = null
      }
    }
  }
}

/**
 * This class represents a list cell for displaying and editing task dependencies in a combo box.
 * It provides a visual representation of a task with its ID and name, and is used within a dropdown editor.
 */
private class TaskListCell : ListCell<DependencyTableModel.TaskComboItem>() {
  override fun updateItem(item: DependencyTableModel.TaskComboItem?, empty: Boolean) {
    super.updateItem(item, empty)
    if (empty || item == null) {
      text = null
      graphic = null
    } else {
      val hBox = HBox(10.0).also {
        it.styleClass.add("predecessor-cell")
      }
      val idLabel = Label(item.myTask.taskID.toString()).apply {
        prefWidth = 30.0
        alignment = Pos.CENTER_RIGHT
        style = "-fx-font-size: 90%;"
      }
      val nameLabel = Label(item.myTask.name).apply {
        val depth = item.myTask.manager.taskHierarchy.getDepth(item.myTask)
        padding = Insets(0.0, 0.0, 0.0, (depth - 1) * 10.0)
      }
      hBox.children.addAll(idLabel, nameLabel)
      graphic = hBox
      text = null
    }
  }
}

