/*
Copyright 2019-2020 BarD Software s.r.o

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
package biz.ganttproject.storage

import biz.ganttproject.FXUtil
import biz.ganttproject.app.DialogController
import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.app.createAlertBody
import biz.ganttproject.storage.cloud.*
import javafx.event.ActionEvent
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.*
import javafx.stage.Screen
import net.sourceforge.ganttproject.GPLogger
import net.sourceforge.ganttproject.IGanttProject
import net.sourceforge.ganttproject.document.Document
import net.sourceforge.ganttproject.document.DocumentManager
import net.sourceforge.ganttproject.document.ReadOnlyProxyDocument
import net.sourceforge.ganttproject.gui.AuthenticationFlow
import net.sourceforge.ganttproject.gui.ProjectUIFacade
import net.sourceforge.ganttproject.language.GanttLanguage
import org.controlsfx.control.NotificationPane
import java.util.*
import java.util.function.Consumer
import kotlin.math.max

fun interface OpenDocumentReceiver {
  /**
   * Tries to open a document and calls authenticationFlow if authentication is required.
   */
  fun call(doc: Document, authenticationFlow: AuthenticationFlow)
}
/**
 * This class builds the storage dialog. Storage dialog shows a list of available storages
 * allows for switching between them and connects storage user interfaces with functions to open or save documents.
 *
 * @author dbarashev@bardsoftware.com
 */
class StorageDialogBuilder(
    private val myProject: IGanttProject,
    projectUi: ProjectUIFacade,
    documentManager: DocumentManager,
    private val cloudStorageOptions: GPCloudStorageOptions,
    private val dialogBuildApi: DialogController) {
  private val myDocumentReceiver: OpenDocumentReceiver
  private val myDocumentUpdater: Consumer<Document>
  private var myNotificationPane: NotificationPane? = null
  private var myOpenStorage: Node? = null
  private var mySaveStorage: Pane? = null

  private val myDialogUi = DialogUi(dialogBuildApi) { myNotificationPane!! }

  init {
    // This will be called when user opens a project.
    myDocumentReceiver = OpenDocumentReceiver { document: Document, authFlow: AuthenticationFlow ->
      val killProgress = myDialogUi.toggleProgress(true)
      val proxyAuthFlow: AuthenticationFlow = { onAuth ->
        killProgress()
        authFlow(onAuth)
      }
      projectUi.openProject(documentManager.getProxyDocument(document), myProject, null, proxyAuthFlow).let { sm ->
        sm.stateCompleted.await {
          myDialogUi.close()
        }
        sm.stateFailed.await { stateFailed ->
          killProgress()
          myDialogUi.error(stateFailed.errorTitle, stateFailed.errorDescription, stateFailed.throwable)
          LOG.error("Failed to open document {}", document.uri, exception = stateFailed.throwable)
        }
      }
    }
    // This will be called when user saves a project.
    myDocumentUpdater = Consumer { document ->
      val killProgress = myDialogUi.toggleProgress(true)
      documentManager.getProxyDocument(document).also {
        it.createContents()
        myProject.document = it
      }

      try {
        if (document.isLocal) {
          document.asLocalDocument()?.create()
        }
        projectUi.saveProject(myProject).await { success ->
          if (success) {
            document.asOnlineDocument()?.let {
              if (it is GPCloudDocument) {
                it.onboard(documentManager, webSocket)
              }
            }
          }
        }
        myDialogUi.toggleProgress(false)
        myDialogUi.close()
      } catch (e: Exception) {
        killProgress()
        if (e is PaymentRequiredException) {
          println(e.message)
        }
        myDialogUi.error("Failed to save the document", e.message ?: "", e)
        LOG.error("Failed to save document {}", document.uri, exception = e)
      }
    }
  }

  fun build(mode: Mode) {
    dialogBuildApi.addStyleClass("dlg-storage")
    dialogBuildApi.addStyleSheet("/biz/ganttproject/storage/StorageDialog.css")
    dialogBuildApi.addStyleSheet("/biz/ganttproject/storage/StorageDialog2.css")
    dialogBuildApi.removeButtonBar()
    dialogBuildApi.setEscCloseEnabled(true)

    val contentPane = BorderPane()
    contentPane.styleClass.addAll("body", "pane-storage")
    contentPane.center = Pane()
    val btnSave = Button(GanttLanguage.getInstance().getText("myProjects.save"))
    val btnOpen = Button(GanttLanguage.getInstance().getText("myProjects.open"))

    val onSaveClicked = {
      showSaveStorageUi(contentPane)
      btnOpen.styleClass.removeAll("selected")
      btnSave.styleClass.add("selected")
    }
    val onOpenClicked = {
      showOpenStorageUi(contentPane)
      btnSave.styleClass.removeAll("selected")
      btnOpen.styleClass.add("selected")
    }
    btnSave.apply {
      addEventHandler(ActionEvent.ACTION) {
        onSaveClicked()
      }
      maxWidth = Double.MAX_VALUE
      styleClass.add("selected")
    }
    btnOpen.apply {
      addEventHandler(ActionEvent.ACTION) {
        onOpenClicked()
      }
      maxWidth = Double.MAX_VALUE

    }

    val titleBox = VBox()
    titleBox.styleClass.add("header")
    val projectName = Label(myProject.projectName)

    val buttonBar = GridPane().apply {
      maxWidth = Double.MAX_VALUE
      columnConstraints.addAll(
          ColumnConstraints().apply { percentWidth = 40.0 },
          ColumnConstraints().apply { percentWidth = 40.0 }
      )
      hgap = 10.0
      styleClass.add("open-save-buttons")
      add(btnSave, 0, 0)
      add(btnOpen, 1, 0)
    }

    titleBox.children.addAll(projectName, buttonBar)
    this.dialogBuildApi.setHeader(titleBox)
    this.dialogBuildApi.setContent(contentPane)
    if (mode == Mode.SAVE) {
      onSaveClicked()
    } else {
      onOpenClicked()
    }
      contentPane.prefHeight = max(Screen.getPrimary().bounds.height / 2, 500.0)
      contentPane.prefWidth = max(Screen.getPrimary().bounds.width / 2, 500.0)
//    contentPane.widthProperty().addListener { _, _, newValue ->
//      contentPaneWidth = newValue.toDouble()
//      if (contentPane.isVisible && contentPaneWidth > 0) {
//        contentPane.prefWidth = contentPaneWidth
//      }
//    }
//    contentPane.heightProperty().addListener { _, _, newValue ->
//      contentPaneHeight = newValue.toDouble()
//      if (contentPane.isVisible && contentPaneHeight > 0) {
//        contentPane.prefHeight = contentPaneHeight
//      }
//    }
  }

  private fun showOpenStorageUi(container: BorderPane) {
    if (myOpenStorage == null) {
      val storagePane = buildStoragePane(Mode.OPEN)
      myNotificationPane = NotificationPane(storagePane).also {
        it.styleClass.addAll(NotificationPane.STYLE_CLASS_DARK)
      }
      myOpenStorage = myNotificationPane
    }
      FXUtil.transitionCenterPane(container, myOpenStorage) {
//      container.scene.window.sizeToScene()
//      container.requestFocus()
    }
  }

  private fun showSaveStorageUi(container: BorderPane) {
    if (mySaveStorage == null) {
      mySaveStorage = buildStoragePane(Mode.SAVE)
    }
    FXUtil.transitionCenterPane(container, mySaveStorage) {
//      container.scene.window.sizeToScene()
//      container.requestFocus()
    }
  }

  private fun buildStoragePane(mode: Mode): Pane {
    return if (myProject.document != null) {
      val storagePane = StoragePane(cloudStorageOptions, myProject.documentManager, ReadOnlyProxyDocument(myProject.document), myDocumentReceiver, myDocumentUpdater, myDialogUi)
      storagePane.buildStoragePane(mode)
    } else {
      Pane(Label("No document!"))
    }
  }

  enum class Mode {
    OPEN, SAVE
  }

  class DialogUi(internal val dialogController: DialogController,
                 private val notificationPane: () -> NotificationPane) {
    fun close() {
      dialogController.hide()
    }

    fun resize() {
      this.dialogController.resize()
    }

    fun error(e: Throwable) {
      dialogController.showAlert(RootLocalizer.create("error.channel.itemTitle"), createAlertBody(e.message ?: ""))
    }

    fun error(title: String, description: String, ex: Throwable?) {
      dialogController.showAlert(title, createAlertBody(description))
    }

    fun message(message: String) {
      val notificationText = TextArea(message)
      notificationText.isWrapText = true
      notificationText.prefRowCount = 3
      notificationText.styleClass.add("info")
      this.notificationPane().graphic = notificationText
      this.notificationPane().show()
    }

    fun toggleProgress(isShown: Boolean): () -> Unit {
      return dialogController.toggleProgress(isShown)
    }
  }
}

/**
 * Storages need to implement this interface to be shown in the storage dialog.
 */
interface StorageUi {
  // Storage category is one of "cloud", "desktop" and "webdav"
  val category: String

  val id get() = category
  // Display name to be shown in the list of storages
  val name: String

  // Creates this stage user interface
  fun createUi(): Pane

  // Creates this storage settings interface
  fun createSettingsUi(): Optional<Pane>

  // Initializes keyboard focus when the UI pane becomes visible
  fun focus() {}
}

// Saved dimensions of the content pane, so that the dialog size was preserved after closing and opening again
private var contentPaneWidth = 0.0
private var contentPaneHeight = 0.0

private val LOG = GPLogger.create("FileDialog")
