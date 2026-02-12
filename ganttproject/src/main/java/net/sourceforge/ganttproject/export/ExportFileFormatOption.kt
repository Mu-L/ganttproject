/*
Copyright 2026 Dmitry Barashev, BarD Software s.r.o

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
package net.sourceforge.ganttproject.export

import biz.ganttproject.core.option.DefaultEnumerationOption
import biz.ganttproject.core.option.ObservableEnum
import biz.ganttproject.core.option.PropertyPaneBuilder

class ExportFileFormatOption<E: Enum<E>>(id: String, initialValue: E, allValues: List<E>): DefaultEnumerationOption<E>(id, allValues) {
  private val observable = ObservableEnum(id, initialValue, allValues)
  init {
    selectedValue = initialValue
    observable.addWatcher{ evt ->
      setSelectedValue(evt.newValue)
    }
  }

  override fun visitPropertyPaneBuilder(builder: PropertyPaneBuilder) {
    builder.dropdown(observable, null)
  }
}

enum class ImageFileFormat(val extension: String) {
  PNG("png"), JPEG("jpg")
}

enum class CsvFileFormat(val extension: String) {
  CSV("csv"), EXCEL("xls")
}