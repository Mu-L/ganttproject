/*
Copyright 2025 Dmitry Barashev,  BarD Software s.r.o

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
package biz.ganttproject.core.table

import biz.ganttproject.FXUtil
import biz.ganttproject.LoggerApi
import biz.ganttproject.lib.fx.GPTreeTableView
import com.sun.javafx.scene.control.behavior.CellBehaviorBase
import javafx.application.Platform
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeTablePosition

/**
 * This class runs operations that somehow modify the tree table structure while keeping the selected tree item,
 * or transferring it to some other tree item appropriately.
 * An example of such operation is collapsing a parent tree item when a child tree item is selected (the selection
 * shall move to the parent in this case) or moving a tree item up and down, indenting or unindenting it.
 */
class SelectionKeeper<NodeType>(
    private val treeTable: GPTreeTableView<NodeType>,
    private val node2treeItem: (NodeType) -> TreeItem<NodeType>?,
    private val logger: LoggerApi<*>
  ) {
  var ignoreSelectionChange = false

  fun keepSelection(keepFocus: Boolean = false, code: ()->Unit) {
    val body = {
      logger.debug(">>> keepSelection")
      val selectedItems = treeTable.selectionModel.selectedItems
      logger.debug("Selected items={}", selectedItems)
      // For every selected node we find a node that will be selected if this node is not found after running the code.
      val selectionReplacements: Map<NodeType, NodeType?> = selectedItems.associate {
        val replacement = it.previousSibling()?.value ?: it.nextSibling()?.value ?: it.parent?.takeUnless { it == treeTable.root }?.value
        it.value to replacement
      }
      logger.debug("Selected nodes={}", selectionReplacements)
      val focusedNode = treeTable.focusModel.focusedItem?.value
      val focusedCell = treeTable.focusModel.focusedCell

      // This way we ignore table selection changes which happen when we manipulate with the tree items in code()
      ignoreSelectionChange = true
      code()
      // Yup, sometimes clearSelection() call is not enough, and selectedIndices remain not empty after it.
      treeTable.selectionModel.clearSelection()
      treeTable.selectionModel.selectedIndices.clear()
      CellBehaviorBase.removeAnchor(treeTable)
      ignoreSelectionChange = false

      val selectedNodes = selectionReplacements
        // If the node disappeared after running code(), we select its replacement.
        .mapNotNull { node2treeItem(it.key) ?: it.value?.let(node2treeItem) }
      logger.debug("Nodes to be selected={}", selectedNodes)
//      treeTable.selectionModel.selectedItems.addAll(selectedNodes)
      val selectedRows = selectedNodes
        .map { node -> treeTable.getRow(node) }
        .toIntArray()
      logger.debug("Selected rows={}", selectedRows)
      treeTable.selectionModel.selectIndices(-1, *selectedRows)

      // Sometimes we need to keep the focus, e.g. when we move some task in the tree, but sometimes we want to focus
      // some other item. E.g. if a task was added due to user action, the user would expect the new task to be focused.
      if (keepFocus) {
        logger.debug("requested to keep focus. Focused node={}", focusedNode)
      }
      if (keepFocus && focusedNode != null) {
        //val liveTask = taskManager.getTask(focusedTask.taskID)
        //logger.debug("live task={}", liveTask)
        node2treeItem(focusedNode)?.let { it ->
          val row = treeTable.getRow(it)
          logger.debug("row to focus={}", it)
          FXUtil.runLater {
            logger.debug("focusing row={} column={}", row, focusedCell.tableColumn)
            Platform.runLater {
              treeTable.focusModel.focus(TreeTablePosition(treeTable, row, focusedCell.tableColumn))
            }
          }
        }
      }
      if (treeTable.editingCell == null) {
        treeTable.requestFocus()
      }
      logger.debug("<<< keepSelection")
    }
    FXUtil.runLater(body)
  }
}