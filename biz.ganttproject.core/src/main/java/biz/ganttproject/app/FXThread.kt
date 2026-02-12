/*
Copyright 2026 Dmitry Barashev,  BarD Software s.r.o

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

import javafx.application.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch

/**
 * A collection of utility functions for interacting with the JavaFX thread.
 */
object FXThread {
  private var isJavaFxAvailable: Boolean? = null
  fun runLater(delayMs: Long, code: ()->Unit) {
    fxScope.launch {
      delay(delayMs)
      runLater { code() }
    }
  }
  fun runLater(code: () -> Unit) {
    val javafxOk = isJavaFxAvailable ?: run {
      try {
        Platform.runLater {}
        true
      } catch (ex: java.lang.IllegalStateException) {
        false
      }
    }
    isJavaFxAvailable = javafxOk
    if (javafxOk) {
      if (Platform.isFxApplicationThread()){
        code()
      } else {
        Platform.runLater(code)
      }
    } else {
      code()
    }
  }

  fun startup(code: () -> Unit) {
    val javafxOk = isJavaFxAvailable ?: run {
      try {
        Platform.runLater {}
        true
      } catch (ex: java.lang.IllegalStateException) {
        false
      }
    }
    if (javafxOk) {
      Platform.runLater(code)
    } else {
      Platform.startup(code)
    }
  }
}

private val fxScope = CoroutineScope(Dispatchers.JavaFx)