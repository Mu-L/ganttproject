/*
Copyright 2021 BarD Software s.r.o

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
package biz.ganttproject.lib.fx

import biz.ganttproject.FXUtil
import biz.ganttproject.app.*
import biz.ganttproject.colorFromUiManager
import biz.ganttproject.core.option.*
import biz.ganttproject.core.time.CalendarFactory
import biz.ganttproject.core.time.GanttCalendar
import biz.ganttproject.lib.fx.treetable.TreeTableCellSkin
import de.jensd.fx.glyphs.GlyphIcon
import de.jensd.fx.glyphs.materialicons.MaterialIcon
import de.jensd.fx.glyphs.materialicons.MaterialIconView
import javafx.application.Platform
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.effect.InnerShadow
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.text.Font
import javafx.util.Callback
import javafx.util.StringConverter
import javafx.util.converter.BigDecimalStringConverter
import javafx.util.converter.DefaultStringConverter
import javafx.util.converter.NumberStringConverter
import net.sourceforge.ganttproject.language.GanttLanguage
import java.math.BigDecimal
import javax.swing.UIManager

data class MyStringConverter<S, T>(
  val toString: (cell: TextCell<S, T>, cellValue: T?) -> String?,
  val fromString: (cell: TextCell<S, T>, stringValue: String) -> T?
)

fun <S, T> StringConverter<T>.adapt(): MyStringConverter<S, T> =
  MyStringConverter(
    toString = { _, cellValue -> this.toString(cellValue) },
    fromString = { _, stringValue -> this.fromString(stringValue) }
  )

val minCellHeight = SimpleDoubleProperty(Font.getDefault().size)
var cellPadding = 20.0
fun calculateMinCellHeight(fontSpec: FontSpec) {
  applicationFontSpec.value = fontSpec
  Font.font(fontSpec.family, fontSpec.getSizePt())?.also { font ->
    FXUtil.runLater {
      applicationFont.set(font)
      minCellHeight.value = font.size + cellPadding
    }
  } ?: run {
    println("font $fontSpec not found")
  }
}
fun initFontProperty(appFontOption: FontOption, rowPaddingOption: DoubleOption) {
  cellPadding = rowPaddingOption.value
  calculateMinCellHeight(appFontOption.value)
  appFontOption.addChangeValueListener { event ->
    event.newValue?.let {
      if (it is FontSpec) {
        calculateMinCellHeight(it)
      }
    }
  }
  rowPaddingOption.addChangeValueListener { event ->
    cellPadding = rowPaddingOption.value
    calculateMinCellHeight(appFontOption.value)
  }
}
val applicationBackground = SimpleObjectProperty(Color.BLACK)
val applicationForeground = SimpleObjectProperty<Paint>(Color.BLACK)
fun initColorProperties() {
  val onChange = {
    applicationBackground.value =
      "TableHeader.background".colorFromUiManager() ?: "Panel.background".colorFromUiManager() ?: Color.WHITE
    applicationForeground.value =
      "TableHeader.foreground".colorFromUiManager() ?: "Panel.foreground".colorFromUiManager() ?: Color.BLACK
  }
  UIManager.addPropertyChangeListener { evt ->
    if ("lookAndFeel" == evt.propertyName && evt.oldValue != evt.newValue) {
      onChange()
    }
  }
  onChange()
}


class TextCell<S, T>(
  private val converter: MyStringConverter<S, T>
) : TreeTableCell<S, T>() {

  private var savedGraphic: Node? = null
  var graphicSupplier: (T) -> Node? = { null }
  private val disclosureNode: Node? get() = parent?.lookup(".arrow")

  private val textField: TextField = createTextField().also {
    it.focusedProperty().addListener { _, oldValue, newValue ->
      // The tree may miss the fact that editing was completed, in particular it happens when user clicks "new task"
      // button while this text field is in the "editing" state. This makes the text field losing the focus, however,
      // we do not receive cancelEdit/commitEdit calls. That's why we have to initiate commitEdit ourselves.
      // However, in other cases this listener is called when we process commitEdit/cancelEdit calls and the
      // flag isCancellingOrCommitting prevents us from re-entering.
      if (oldValue && !newValue && !isCancellingOrCommitting) {
        commitEdit()
      }
    }
  }

  private var isCancellingOrCommitting = false
  // This hook will transition the newTaskActor from EDITING_STARTED to IDLE state, thus enabling creation of the new
  // tasks. This should always be called from commitEdit/cancelEdit methods and should be called from those branches
  // of startEdit() where we break the execution.
  internal var onEditingCompleted: ()->Unit = {}

  override fun createDefaultSkin(): Skin<*> {
    return (treeTableView as? GPTreeTableView<S>)?.let {
      TreeTableCellSkin(this) {
        it.onProperties()
      }
    } ?: TreeTableCellSkin(this) {}
  }

  init {
    styleClass.add("gp-tree-table-cell")
//    registerCell(this)
  }

  override fun startEdit() {
    if (this.index == -1) {
      return
    }
    if (!isEditable) {
      onEditingCompleted()
      return
    }
    super.startEdit()
    contentDisplay = ContentDisplay.GRAPHIC_ONLY
    disclosureNode?.let {
      it.isVisible = false
    }

    if (isEditing) {
      treeTableView.requestFocus()
      doStartEdit()
    } else {
      onEditingCompleted()
    }
  }

  private fun doStartEdit() {
    textField.text = getItemText()
    text = " "
    savedGraphic = graphic
    graphic = textField


    // requesting focus so that key input can immediately go into the
    // TextField (see RT-28132)
    Platform.runLater {
      textField.selectAll()
      textField.requestFocus()
    }
  }

  override fun cancelEdit() {
    if (this.index == -1) {
      return
    }
    this.isCancellingOrCommitting = true
    try {
      if (treeTableView.editingCell != null) {
        super.cancelEdit()
      }
      styleClass.remove("validation-error")
    } finally {
      onEditingCompleted()
    }
    val rowIdx = this.tableRow.index
    val col = this.tableColumn
    Platform.runLater {
      doCancelEdit(rowIdx, col)
      this.isCancellingOrCommitting = false
    }
  }

  private fun doCancelEdit(rowIndex: Int, col: TreeTableColumn<S,*>) {
    text = getItemText()
    graphic = savedGraphic
    savedGraphic = null
    disclosureNode?.let {
      it.isVisible = true
    }
    treeTableView.requestFocus()
    if (rowIndex != -1) {
      // It seems that the cell is not recreated after cancelling edit (e.g. with Escape) and we get a black
      // rectangle instead of a task name. Moving focus back and forth re-creates the cell.
      treeTableView.focusModel.focus(-1)
      treeTableView.focusModel.focus(rowIndex, col)
    }
  }

  override fun commitEdit(newValue: T?) {
    if (this.index == -1) {
      return
    }
    this.isCancellingOrCommitting = true
    try {
      disclosureNode?.let {
        it.isVisible = true
      }
      if (treeTableView.editingCell != null) {
        super.commitEdit(newValue)
      }
    } finally {
      this.isCancellingOrCommitting = false
      onEditingCompleted()
    }
    treeTableView.requestFocus()
    graphic = savedGraphic
    savedGraphic = null
  }

  fun commitEdit() {
    commitEdit(converter.fromString(this, textField.text))
  }

  private fun commitText(text: String) = commitEdit(converter.fromString(this, text))

  override fun updateItem(cellValue: T?, empty: Boolean) {
    super.updateItem(cellValue, empty)
    updateCellClasses(this, empty)
    doUpdateItem()
  }

  private fun doUpdateItem() {
    if (isEmpty) {
      text = null
      graphic = null
    } else {
      doUpdateFilledItem()
    }
  }

  private fun doUpdateFilledItem() {
    if (isEditing) {
      textField.text = getItemText()
      text = null
      graphic = textField
    } else {
      text = getItemText()
      graphic = graphicSupplier(this.item)
      contentDisplay = ContentDisplay.RIGHT
    }
  }

  private fun getItemText() = converter.toString(this, this.item) ?: ""
  private fun createTextField() =
    TextField(getItemText()).also { textField ->
      //textField.prefWidth = this.width
      // Use onAction here rather than onKeyReleased (with check for Enter),
      // as otherwise we encounter RT-34685
      textField.onAction = EventHandler { event: ActionEvent ->
        effect = try {
          commitText(textField.text)
          styleClass.remove("validation-error")
          null
        } catch (ex: ValidationException) {
          styleClass.add("validation-error")
          InnerShadow(10.0, Color.RED)
        } finally {
          event.consume()
        }
      }
      textField.onKeyPressed = EventHandler { event ->
        if (event.code == KeyCode.INSERT && event.getModifiers() == 0) {
          try {
            commitText(textField.text)
            styleClass.remove("validation-error")
          } catch (ex: ValidationException) {
            styleClass.add("validation-error")
          }
          finally {
            event.consume()
          }
        }
      }
    }
}

fun <S> createTextColumn(
  name: String,
  getValue: (S) -> String?,
  setValue: (S, String) -> Unit,
  onEditCompleted: (S) -> Unit = {}): TreeTableColumn<S, String> =
  TreeTableColumn<S, String>(name).apply {
    setCellValueFactory {
      ReadOnlyStringWrapper(getValue(it.value.value) ?: "")
    }
    cellFactory = TextCellFactory<S, String>(converter = DefaultStringConverter().adapt()) {
      it.styleClass.add("text-left")
      it.isDisable = !this@apply.isEditable
      it.isEditable = this@apply.isEditable
    }
    onEditCommit = EventHandler { event ->
      setValue(event.rowValue.value, event.newValue)
    }
    onEditCancel = EventHandler { event ->
      onEditCompleted(event.rowValue.value)
    }
  }

class GanttCalendarStringConverter : StringConverter<GanttCalendar>() {
  private val validator = createStringDateValidator(null) {
    listOf(GanttLanguage.getInstance().shortDateFormat)
  }
  override fun toString(value: GanttCalendar?) = value?.toString() ?: ""

  override fun fromString(text: String): GanttCalendar =
    CalendarFactory.createGanttCalendar(validator.parse(text))
}

fun <S> createDateColumn(name: String, getValue: (S) -> GanttCalendar?, setValue: (S, GanttCalendar) -> Unit): TreeTableColumn<S, GanttCalendar> =
  TreeTableColumn<S, GanttCalendar>(name).apply {
    setCellValueFactory {
      ReadOnlyObjectWrapper(getValue(it.value.value))
    }
    val converter = GanttCalendarStringConverter()
    cellFactory = Callback { TextCell<S, GanttCalendar>(converter.adapt()).also {
      it.styleClass.add("text-left")
      it.isDisable = !this@apply.isEditable
      it.isEditable = this@apply.isEditable
    } }
    onEditCommit = EventHandler { event -> setValue(event.rowValue.value, event.newValue) }
  }

fun <S> createBooleanColumn(name: String, getValue: (S) -> Boolean?, setValue: (S, Boolean) -> Unit) =
  TreeTableColumn<S, Boolean>(name).apply {
    setCellValueFactory {
      SimpleBooleanProperty(getValue(it.value.value) ?: false)
    }
    cellFactory =  Callback { CheckBoxTableCell<S>().also {
      it.isEditable = this.isEditable
    } }
    onEditCommit = EventHandler {event -> setValue(event.rowValue.value, event.newValue) }
  }

fun <S> createIntegerColumn(name: String, getValue: (S) -> Int?, setValue: (S, Int) -> Unit) =
  TreeTableColumn<S, Number>(name).apply {
    setCellValueFactory {
      ReadOnlyObjectWrapper(getValue(it.value.value))
    }
    cellFactory = Callback {
      TextCell<S, Number>(NumberStringConverter(getNumberFormat()).adapt()).also {
        it.styleClass.add("text-right")
        it.isDisable = !this@apply.isEditable
        it.isEditable = this@apply.isEditable
      }
    }
    onEditCommit = EventHandler { event -> setValue(event.rowValue.value, event.newValue.toInt()) }
  }

fun <S> createDoubleColumn(name: String, getValue: (S) -> Double?, setValue: (S, Double) -> Unit) =
  TreeTableColumn<S, Number>(name).apply {
    setCellValueFactory {
      ReadOnlyObjectWrapper(getValue(it.value.value))
    }
    cellFactory = Callback {
      TextCell<S, Number>(NumberStringConverter(getNumberFormat()).adapt()).also {
        it.styleClass.add("text-right")
        it.isDisable = !this@apply.isEditable
        it.isEditable = this@apply.isEditable
      }
    }
    onEditCommit = EventHandler { event -> setValue(event.rowValue.value, event.newValue.toDouble()) }
  }

fun <S> createDecimalColumn(name: String, getValue: (S) -> BigDecimal?, setValue: (S, BigDecimal) -> Unit) =
  TreeTableColumn<S, BigDecimal>(name).apply {
    setCellValueFactory {
      ReadOnlyObjectWrapper(getValue(it.value.value))
    }
    cellFactory = Callback {
      val converter = MyStringConverter<S, BigDecimal>(
        toString = { _, value ->
          value?.let { getNumberFormat().format(it) } ?: ""
        },
        fromString = { _, value -> BigDecimalStringConverter().fromString(value) }
      )
      TextCell(converter).also {
        it.styleClass.add("text-right")
        it.isDisable = !this@apply.isEditable
        it.isEditable = this@apply.isEditable
      }
    }
    onEditCommit = EventHandler { event -> setValue(event.rowValue.value, event.newValue.toDouble().toBigDecimal()) }
  }

fun <S, T> createIconColumn(name: String, getValue: (S) ->T?, iconFactory: (T) -> GlyphIcon<*>?, i18n: Localizer) =
  TreeTableColumn<S, T>(name).apply {
    setCellValueFactory {
      ReadOnlyObjectWrapper(getValue(it.value.value))
    }
    cellFactory = Callback {
      val cell = TextCell<S, T>(MyStringConverter(
        toString = { _, value -> i18n.formatText(value?.toString()?.lowercase() ?: "") },
        fromString = { _, _ -> null}
      ))
      cell.graphicSupplier = {
        iconFactory(it)
      }
      cell.contentDisplay = ContentDisplay.LEFT
      cell.alignment = Pos.CENTER_LEFT

      cell
    }
  }

/**
 * Creates a column that shows values from the given list. Its editor is a dropdown/
 */
fun <ObjectType, ChoiceType> createChoiceColumn(
  name: String,
  getValue: (ObjectType) -> ChoiceType?,
  setValue: (ObjectType, ChoiceType) -> Unit,
  allValues: ()->List<ChoiceType>,
  stringConverter: StringConverter<ChoiceType?>,
  isEditableCell: (ObjectType)->Boolean = {true}) = TreeTableColumn<ObjectType, ChoiceType>(name).apply {

  setCellValueFactory {
    ReadOnlyObjectWrapper(getValue(it.value.value))
  }
  cellFactory = Callback {
    DropdownTableCell<ObjectType, ChoiceType?>(allValues, stringConverter, isEditableCell)
  }
  onEditCommit = EventHandler { event ->
    setValue(event.rowValue.value, event.newValue)
  }
}

class TextCellFactory<S, T>(
  private val converter: MyStringConverter<S, T>,
  private val cellSetup: (TextCell<S, T>) -> Unit = {}
): Callback<TreeTableColumn<S, T>, TreeTableCell<S, T>> {
  internal var editingCell: TextCell<S, T>? = null

  override fun call(param: TreeTableColumn<S, T>?) =
    TextCell(converter).also(cellSetup)
}

class CheckBoxTableCell<S> : TreeTableCell<S, Boolean>() {
  private var isChecked = false
  set(value) {
    field = value
    button.graphic = MaterialIconView(
      if (value) MaterialIcon.CHECK else MaterialIcon.CHECK_BOX_OUTLINE_BLANK
    ).also {
      it.glyphSize = minCellHeight.value * 0.6
    }
  }
  private val button = Button("", null).also {
    it.onAction = EventHandler {
      val newValue = !isChecked
      this.treeTableView.edit(this.tableRow.index, this.tableColumn)
      commitEdit(newValue)
      isChecked = newValue
    }
  }

  init {
    styleClass.add("gp-check-box-tree-table-cell")
    button.disableProperty().bind(editableProperty().not())
  }

  override fun updateItem(item: Boolean?, empty: Boolean) {
    super.updateItem(item, empty)
    updateCellClasses(this, empty)
    if (empty) {
      text = null
      graphic = null
    } else {
      contentDisplay = ContentDisplay.GRAPHIC_ONLY
      alignment = Pos.CENTER
      graphic = button
      isChecked = item ?: false
    }
  }
}

/**
 * This cell contains a dropdown control that allows for choosing from a list of available values.
 */
class DropdownTableCell<ObjectType, ChoiceType>(
  allValues: () -> List<ChoiceType>,
  stringConverter: StringConverter<ChoiceType>,
  private val isEditableCell: (ObjectType) -> Boolean
) : TreeTableCell<ObjectType, ChoiceType>() {
  private val editor = createDropdownEditor(allValues(), stringConverter).also { dropdown ->
    dropdown.onAction = EventHandler {
      val newValue = dropdown.value
      this.treeTableView.edit(this.tableRow.index, this.tableColumn)
      commitEdit(newValue)
    }
  }

  init {
    styleClass.add("gp-dropdown-tree-table-cell")
  }

  override fun updateItem(item: ChoiceType, empty: Boolean) {
    super.updateItem(item, empty)
    updateCellClasses(this, empty)
    if (empty) {
      text = null
      graphic = null
    } else {
      contentDisplay = ContentDisplay.GRAPHIC_ONLY
      alignment = Pos.CENTER_LEFT
      val node = this.treeTableView.getTreeItem(this.tableRow.index).value
      if (isEditableCell(node)) {
        graphic = editor
        if (editor.value != item) {
          editor.value = item
        }
      } else {
        graphic = null
      }
    }
  }
}

fun <ChoiceType> createDropdownEditor(
  allValues: List<ChoiceType>,
  stringConverter: StringConverter<ChoiceType>): ComboBox<ChoiceType> {

  return ComboBox(FXCollections.observableArrayList(allValues)).also { comboBox ->
    comboBox.converter = stringConverter
  }
}

private fun <S> updateCellClasses(cell: TreeTableCell<S, *>, empty: Boolean) {
  if (cell.treeTableView.focusModel.isFocused(cell.tableRow.index, cell.tableColumn)) {
    if (cell.styleClass.indexOf("focused") < 0) {
      cell.styleClass.add("focused")
    }
  } else {
    cell.styleClass.removeAll("focused")
  }
  if (cell.treeTableView.selectionModel.isSelected(cell.tableRow.index)) {
    if (cell.styleClass.indexOf("selected") < 0) {
      cell.styleClass.add("selected")
    } else {
      cell.styleClass.removeAll("selected")
    }
  }
  cell.styleClass.removeAll("odd")
  cell.styleClass.removeAll("even")
  if (!empty) {
    if (cell.tableRow.index % 2 == 0) {
      cell.styleClass.add("even")
    } else {
      cell.styleClass.add("odd")
    }
  }
}
