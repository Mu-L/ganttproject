/*
Copyright 2022 BarD Software s.r.o.

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
package biz.ganttproject.customproperty

import net.sourceforge.ganttproject.storage.ProjectDatabase

class CalculatedPropertyUpdater(
  private val projectDatabase: ProjectDatabase,
  private val tableName: String,
  private val customPropertyManager: CustomPropertyManager,
  private val propertyHolders: ()->List<CustomPropertyHolder>) {

  //private var calculatedDefs: List<CustomPropertyDefinition> = emptyList()

  fun update() {
    customPropertyManager.definitions.filter {
      when (val m = it.calculationMethod) {
        is SimpleSelect<*> -> m.tableName == tableName
        else -> false
      }
    }.mapNotNull {
      when (val calculationMethod = it.calculationMethod) {
        is SimpleSelect<*> -> calculationMethod.selectExpression
        else -> null
      }
    }
  }
}