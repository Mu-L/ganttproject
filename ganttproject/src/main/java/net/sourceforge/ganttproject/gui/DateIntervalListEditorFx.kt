/*
Copyright 2026 BarD Software s.r.o, Alexander Popov

This file is part of GanttProject, an open-source project management tool.

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

import biz.ganttproject.app.DialogController
import biz.ganttproject.app.FXThread
import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.app.dialog
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import net.sourceforge.ganttproject.calendar.MultiDatePicker
import net.sourceforge.ganttproject.gui.DateIntervalListEditor.DateInterval
import net.sourceforge.ganttproject.gui.DateIntervalListEditor.DateIntervalModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class DateIntervalListEditorFx(private val model: DateIntervalModel) : HBox() {
  private val listView = ListView<DateInterval>()
  private val tableActionsModel = object : TableActionsModel<DateInterval> {
    override fun getAllItems(): List<DateInterval> = model.intervals.toList()

    override fun delete(indices: IntArray) {
      FXThread.runLater {
        indices.sortedDescending().forEach { index ->
          model.remove(model.intervals[index])
        }
        refreshTable()
      }
    }

    override fun onAdd() {
      showDatePicker { interval ->
        model.add(interval)
        refreshTable()
      }
    }

    override fun refreshTable() {
      listView.items.setAll(model.intervals.toList())
    }

    override val selectedIndices: ObservableList<Int>
      get() = listView.selectionModel.selectedIndices
    override val selectedItems: ObservableList<DateInterval>
      get() = listView.selectionModel.selectedItems

    override fun select(index: Int) {
      listView.selectionModel.select(index)
    }

    override fun clearSelection() {
      listView.selectionModel.clearSelection()
    }

    override fun scrollTo(index: Int) {
      listView.scrollTo(index)
    }

    override fun requestFocus() {
      listView.requestFocus()
    }

    override var isDisable: Boolean
      get() = listView.isDisable
      set(value) {
        listView.isDisable = value
      }
    override val isDisabled: Boolean
      get() = listView.isDisable
    override val itemCount: Int
      get() = listView.items.size
  }

  private val component = AbstractTableAndActionsComponentFx(listView, tableActionsModel)

  init {
    stylesheets.add("/biz/ganttproject/lib/MultiDatePicker.css")
    stylesheets.add("/biz/ganttproject/task/TaskPropertiesDialog.css")
    stylesheets.add("/biz/ganttproject/app/tables.css")
    stylesheets.add("/biz/ganttproject/app/buttons.css")
    styleClass.addAll("tab-contents")

    listView.setCellFactory {
      object : ListCell<DateInterval>() {
        override fun updateItem(item: DateInterval?, empty: Boolean) {
          super.updateItem(item, empty)
          if (empty || item == null) {
            text = null
            graphic = null
          } else {
            text = model.format(item)
          }
        }
      }
    }
    tableActionsModel.refreshTable()
    children.add(component.createDefaultLayout().apply {
      HBox.setHgrow(this, Priority.ALWAYS)
    })
  }
}

fun showDatePicker(addInterval: (DateInterval)->Unit) {
  val title = RootLocalizer.formatText("calendar.editor.datePickerDialog.title")
  dialog(title, "calendar.event.add") { controller: DialogController ->
    controller.addStyleSheet("/biz/ganttproject/app/Dialog.css")
    controller.addStyleSheet("/biz/ganttproject/lib/MultiDatePicker.css")
    controller.addStyleClass("dlg")
    val multiDatePicker = MultiDatePicker()
    multiDatePicker.value = LocalDate.now()

    controller.setContent(multiDatePicker.popupContent)

    controller.setupButton(ButtonType.APPLY) { button: Button ->
      button.styleClass.add("btn-attention")
      button.text = RootLocalizer.formatText("add")
      button.setOnAction {
        val selectedDates = multiDatePicker.selectedDates
        if (selectedDates.isNotEmpty()) {
          val start = Date.from(selectedDates.first().atStartOfDay(ZoneId.systemDefault()).toInstant())
          val end = Date.from(selectedDates.last().atStartOfDay(ZoneId.systemDefault()).toInstant())
          addInterval(DateInterval.createFromVisibleDates(start, end))
        }
        controller.hide()
      }
    }
  }
}

