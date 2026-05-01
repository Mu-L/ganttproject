/*
Copyright 2025 Dmitry Barashev, BarD Software s.r.o

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
package biz.ganttproject.app

import biz.ganttproject.printCss
import biz.ganttproject.walkTree
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Label
import javafx.scene.layout.HBox


class ErrorPane {
  enum class MessageType {
    ERROR, WARNING, INFO
  }
  val hasErrorsProperty = SimpleBooleanProperty(false)
  val fxNode by lazy {
    errorPane.styleClass.addAll(boxStyleClass, "noerror")
    errorLabel.styleClass.addAll(labelStyleClass, "hint")
    errorPane
  }

  var boxStyleClass = "hint-validation-pane"
  var labelStyleClass = "hint-validation"

  private val errorLabel = Label().also {
    it.isWrapText = true
  }

  private val errorPane = HBox().also {
    it.children.add(errorLabel)
  }

  fun onError(it: String?) {
    message(it, MessageType.ERROR)
  }

  fun message(it: String?, messageType: MessageType) {
    if (it.isNullOrBlank()) {
      errorPane.isVisible = false
      errorPane.styleClass.removeAll(MessageType.entries.map { it.name.lowercase() })
      if (!errorPane.styleClass.contains("noerror")) {
        errorPane.styleClass.add("noerror")
      }
      errorLabel.text = ""
      hasErrorsProperty.value = false
    }
    else {
      errorLabel.text = it
      errorPane.isVisible = true
      errorPane.styleClass.remove("noerror")
      errorPane.styleClass.add(messageType.name.lowercase())
      errorPane.walkTree { it.printCss() }
      hasErrorsProperty.value = true
    }
  }

  fun warning(msg: String) {
    message (msg, MessageType.WARNING)
  }

  fun clear() = onError(null)
}