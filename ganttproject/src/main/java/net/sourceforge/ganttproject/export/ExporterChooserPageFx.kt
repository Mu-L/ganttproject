
package net.sourceforge.ganttproject.export

import biz.ganttproject.app.*
import javafx.scene.Node
import net.sourceforge.ganttproject.export.ExportFileWizardImpl.State
import net.sourceforge.ganttproject.gui.projectwizard.WizardPage
import java.awt.Component

class ExporterChooserPageFx(exporters: List<Exporter>, private val model: State) : WizardPage {
  override val title: String = i18n.formatText("option.exporter.title")
  override val component: Component? = null

  private val titles = exporters.flatMapIndexed { index, exporter -> listOf(
    "title.$index" to { LocalizedString(exporter.fileTypeDescription, DummyLocalizer)},
    "title.$index.help" to { LocalizedString("", DummyLocalizer)}
  )}.toMap()

  override val fxComponent: Node? by lazy {
    val optionPaneBuilder = OptionPaneBuilder<Exporter>().apply {
      this.i18n = MappingLocalizer(titles, DummyLocalizer::create)
      this.styleClass = "exporter-chooser-page"
      elements = exporters.mapIndexed { index, exporter ->
        OptionElementData("title.${index}", exporter, isSelected = (index == 0),
          customContent = buildCustomContent(exporter))
      }
      onSelect = { model.exporter = it }
    }
    optionPaneBuilder.buildPane()
  }

  private fun buildCustomContent(exporter: Exporter): Node? {
    return if (exporter.options.options.isEmpty()) null
    else
    properties(propertyLocalizer) {
      exporter.options.options.forEach {
        it.visitPropertyPaneBuilder(this)
      }
    }
  }

  override fun setActive(b: Boolean) {

  }
}

private val propertyLocalizer = i18n {
  default()
  prefix("option") {
    fallback {
      default()
      transform {
        val replaced =
          if (it.contains(".format.value")) it.replace(".format.value", ".fileformat")
          else if (it.contains("fileformat.value")) it.replace(".value", "")
          else it
        "optionValue.$replaced.label"
      }
      //debug("exporter.dropdown.value")
    }
  }
}
private val i18n = RootLocalizer