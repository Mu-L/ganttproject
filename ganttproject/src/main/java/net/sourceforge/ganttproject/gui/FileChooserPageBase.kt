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
package net.sourceforge.ganttproject.gui

import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.core.option.BooleanOption
import biz.ganttproject.core.option.DefaultBooleanOption
import biz.ganttproject.core.option.GPOptionGroup
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import javafx.beans.property.SimpleObjectProperty
import net.sourceforge.ganttproject.document.Document
import net.sourceforge.ganttproject.gui.options.OptionsPageBuilder
import net.sourceforge.ganttproject.gui.projectwizard.WizardPage
import org.osgi.service.prefs.Preferences
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileFilter

/**
 * Base class for the file chooser pages in the Import and Export wizards.
 */
abstract class FileChooserPageBase protected constructor(
  private val myDocument: Document?,
  uiFacade: UIFacade?,
  val fileChooserTitle: String?,
  val pageTitle: String?,
  val fileChooserSelectionMode: Int = JFileChooser.FILES_ONLY
) : WizardPage {
  protected val chooser: TextFieldAndFileChooserComponent = object : TextFieldAndFileChooserComponent(uiFacade, this.fileChooserTitle) {
    override fun onFileChosen(file: File?) {
      tryChosenFile(file)
    }
  }

  abstract val preferences: Preferences

  private val myOptionsBuilder: OptionsPageBuilder = OptionsPageBuilder().also {
    it.i18N = object : OptionsPageBuilder.I18N() {
      protected override fun hasValue(key: String): Boolean {
        return if (key == getCanonicalOptionLabelKey(overwriteOption) + ".trailing") true else super.hasValue(
          key
        )
      }

      protected override fun getValue(key: String): String? {
        if (key == getCanonicalOptionLabelKey(overwriteOption) + ".trailing") {
          return RootLocalizer.formatText("document.overwrite")
        }
        return super.getValue(key)
      }
    }
  }
  private val mySecondaryOptionsComponent = JPanel(BorderLayout()).also {
    it.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0))
  }
  private val myFileLabel = JLabel("")
  protected val overwriteOption: BooleanOption = DefaultBooleanOption("overwrite")

  val selectedFileProperty: SimpleObjectProperty<File?> = SimpleObjectProperty<File?>(null)
  protected var hasOverwriteOption: Boolean = true
  protected var updateChosenFile: (File) -> File = { it }
  protected var proposeChosenFile: () -> File = { File(defaultFileName) }

  protected val defaultFileName: String
    get() = if (myDocument == null) "document.gan" else myDocument.getFileName()


  override val title: String get() = pageTitle ?: ""

  fun tryChosenFile(file: File?) {
    myFileLabel.setOpaque(true)
    validateFile(file).onSuccess {
      selectedFileProperty.set(file)
      UIUtil.clearErrorLabel(myFileLabel)
      preferences.put(PREF_SELECTED_FILE, chooser.file.absolutePath)
    }.onFailure {
      UIUtil.setupErrorLabel(myFileLabel, it)
    }
  }

  override val component: Component by lazy {
    val myComponent = JPanel(BorderLayout())
    chooser.setFileSelectionMode(this.fileChooserSelectionMode)
    val contentPanel: JComponent = JPanel(BorderLayout())
    val fileBox = Box.createVerticalBox()
    chooser.setAlignmentX(Component.LEFT_ALIGNMENT)
    fileBox.add(this.chooser)
    myFileLabel.setAlignmentX(Component.LEFT_ALIGNMENT)
    fileBox.add(myFileLabel)
    if (hasOverwriteOption) {
      fileBox.add(
        myOptionsBuilder.createOptionComponent(
          GPOptionGroup("exporter", this.overwriteOption), this.overwriteOption
        )
      )
    }
    contentPanel.add(fileBox, BorderLayout.NORTH)
    contentPanel.add(mySecondaryOptionsComponent, BorderLayout.CENTER)
    myComponent.add(contentPanel, BorderLayout.NORTH)
    myComponent
  }

  protected open fun loadPreferences() {
    val oldFile = preferences.get(PREF_SELECTED_FILE, null)
    if (oldFile != null) {
      chooser.setFile(updateChosenFile(File(oldFile)))
    } else {
      chooser.setFile(proposeChosenFile())
    }
  }

  override fun setActive(isActive: Boolean) {
    val optionGroups = this.optionGroups
    if (isActive == false) {
      for (optionGroup in optionGroups) {
        optionGroup.commit()
      }
      if (chooser.file != null) {
        preferences.put(PREF_SELECTED_FILE, chooser.file.absolutePath)
      }
    } else {
      for (optionGroup in optionGroups) {
        optionGroup.lock()
      }
      mySecondaryOptionsComponent.removeAll()
      mySecondaryOptionsComponent.add(createSecondaryOptionsPanel(), BorderLayout.NORTH)
      chooser.setFileFilter(createFileFilter())
      loadPreferences()
    }
  }

  protected open fun createSecondaryOptionsPanel(): Component {
    return myOptionsBuilder.buildPlanePage(this.optionGroups.toTypedArray())
  }

  protected abstract fun createFileFilter(): FileFilter?

  protected abstract val optionGroups: List<GPOptionGroup>

  protected open fun validateFile(file: File?): Result<File?, String?> {
    return basicValidateFile(file)
  }
}

const val PREF_SELECTED_FILE: String = "selected_file"
