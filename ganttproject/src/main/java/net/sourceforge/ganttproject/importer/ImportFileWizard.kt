/*
 * Copyright (c) 2011-2026 Dmitry Barashev, BarD Software s.r.o.
 *
 * This file is part of GanttProject, an open-source project management tool.
 *
 * GanttProject is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * GanttProject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.ganttproject.importer

import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.core.option.GPOptionGroup
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import javafx.scene.control.Label
import net.sourceforge.ganttproject.IGanttProject
import net.sourceforge.ganttproject.filter.ExtensionBasedFileFilter
import net.sourceforge.ganttproject.gui.FileChooserPageBase
import net.sourceforge.ganttproject.gui.UIFacade
import net.sourceforge.ganttproject.gui.projectwizard.WizardModel
import net.sourceforge.ganttproject.gui.projectwizard.WizardPage
import net.sourceforge.ganttproject.gui.projectwizard.showWizard
import net.sourceforge.ganttproject.plugins.PluginManager.*
import org.osgi.service.prefs.Preferences
import java.awt.Component
import java.io.File
import javax.swing.filechooser.FileFilter

/**
 * Wizard for importing files into a Gantt project.
 */
class ImportFileWizard(uiFacade: UIFacade, project: IGanttProject, pluginPreferences: Preferences,
                       importers: List<Importer> = getImporters()) {
  private val wizardModel = ImporterWizardModel()
  init {
    importers.forEach { it.setContext(project, uiFacade, pluginPreferences) }
    val filePage = ImportFileChooserPage(wizardModel, project, pluginPreferences, uiFacade)
    filePage.selectedFileProperty.addListener { _, _, _ ->
      wizardModel.needsRefresh.set(true, this)
    }
    wizardModel.addPage(ImporterChooserPage(importers, uiFacade, pluginPreferences, wizardModel))
    wizardModel.addPage(filePage)
    wizardModel.customPageProperty.addListener { _, oldValue, newValue ->
      if (oldValue == null && newValue != null) {
        wizardModel.addPage(newValue)
      } else if (oldValue != null && newValue == null) {
        wizardModel.removePage(oldValue)
      }
    }
  }

  fun show() {
    showWizard(wizardModel)
  }
}

/**
 * Model for the import wizard, managing importer and file selection.
 */
class ImporterWizardModel: WizardModel() {
  // Selected importer. Updates a customPageProperty with the custom page of the importer, if any.
  var importer: Importer? = null
    set(value) {
      field = value
      customPageProperty.set(null)
      value?.customPage?.let { customPageProperty.set(it) }
    }

  // Selected file.
  var file: File? = null
    set(value) {
      field = value
      importer?.setFile(value)
    }

  // Some importers, e.g. ICS importer, provide a custom page that is appended to the wizard.
  val customPageProperty = SimpleObjectProperty<WizardPage?>(null)

  init {
    canFinish = { importer != null && file != null }
    hasNext = { when (currentPage) {
      0 -> importer != null
      1 -> customPageProperty.get() != null && file != null
      else -> false
    } }
    onOk = { importer?.run() }
  }
}

private fun getImporters(): MutableList<Importer> {
  return getExtensions(Importer.EXTENSION_POINT_ID, Importer::class.java)
}

private class ImporterChooserPageFx(
  importers: List<Importer>,
  uiFacade: UIFacade,
  pluginPreferences: Preferences,
  wizardModel: ImporterWizardModel
) : WizardPage {
  override val title: String = i18n.formatText("importerChooserPageTitle")
  override val component: Component?
    get() = null

  override fun setActive(b: Boolean) {
  }

  override val fxComponent: Node by lazy {
    Label("foo")
  }
}
/**
 * Wizard page for choosing a file to import from.
 */
private class ImportFileChooserPage(
  private val state: ImporterWizardModel, project: IGanttProject, prefs: Preferences, uiFacade: UIFacade)
  : FileChooserPageBase(prefs, project.document, uiFacade, fileChooserTitle = "",
  pageTitle = i18n.formatText("importerFileChooserPageTitle")) {

  val importer get() = state.importer

  init {
    hasOverwriteOption = false
    selectedFileProperty.addListener { value, file, newValue ->
      state.file = newValue
    }
  }

  override fun createFileFilter(): FileFilter? =
    importer?.let {
      return ExtensionBasedFileFilter(it.getFileNamePattern(), it.getFileTypeDescription())
    }


  override val optionGroups: List<GPOptionGroup> = emptyList()

  override fun validateFile(file: File?): Result<File?, String?> {
    return super.validateFile(file).andThen { file ->
      if (file?.isDirectory ?: false) {
        Err("It is a directory")
      } else {
        Ok(file)
      }
    }
  }
}

private val i18n = RootLocalizer
