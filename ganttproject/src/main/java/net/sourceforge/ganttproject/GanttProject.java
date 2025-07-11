/*
Copyright 2002-2019 Alexandre Thomas, BarD Software s.r.o

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
package net.sourceforge.ganttproject;

import biz.ganttproject.LoggerApi;
import biz.ganttproject.app.*;
import biz.ganttproject.lib.fx.TreeTableCellsKt;
import biz.ganttproject.platform.UpdateOptions;
import biz.ganttproject.storage.cloud.GPCloudOptions;
import biz.ganttproject.storage.cloud.GPCloudStatusBar;
import com.beust.jcommander.Parameter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import javafx.stage.Stage;
import kotlin.Unit;
import net.sourceforge.ganttproject.action.*;
import net.sourceforge.ganttproject.action.edit.EditMenu;
import net.sourceforge.ganttproject.action.help.HelpMenu;
import net.sourceforge.ganttproject.action.project.ProjectMenu;
import net.sourceforge.ganttproject.action.resource.ResourceActionSet;
import net.sourceforge.ganttproject.action.view.ViewMenu;
import net.sourceforge.ganttproject.action.zoom.ZoomActionSet;
import net.sourceforge.ganttproject.chart.GanttChart;
import net.sourceforge.ganttproject.chart.TimelineChart;
import net.sourceforge.ganttproject.document.Document;
import net.sourceforge.ganttproject.document.Document.DocumentException;
import net.sourceforge.ganttproject.gui.UIConfiguration;
import net.sourceforge.ganttproject.gui.UIUtil;
import net.sourceforge.ganttproject.gui.scrolling.ScrollingManager;
import net.sourceforge.ganttproject.gui.view.ViewProvider;
import net.sourceforge.ganttproject.io.GPSaver;
import net.sourceforge.ganttproject.io.GanttXMLOpen;
import net.sourceforge.ganttproject.io.GanttXMLSaver;
import net.sourceforge.ganttproject.language.GanttLanguage;
import net.sourceforge.ganttproject.language.GanttLanguage.Event;
import net.sourceforge.ganttproject.parser.GPParser;
import net.sourceforge.ganttproject.parser.ParserFactory;
import net.sourceforge.ganttproject.resource.HumanResourceManager;
import net.sourceforge.ganttproject.resource.ResourceEvent;
import net.sourceforge.ganttproject.resource.ResourceView;
import net.sourceforge.ganttproject.roles.RoleManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main frame of the project
 */
public class GanttProject extends GanttProjectBase implements ResourceView, GanttLanguage.Listener {

  private final LoggerApi<Logger> boundsLogger = GPLogger.create("Window.Bounds");
  private final LoggerApi<Logger> gpLogger = GPLogger.create("GanttProject");

  // Chart component of the Gantt chart view.
  private final GanttGraphicArea area;

  // Chart component of the resource load view.
  private final ResourceLoadGraphicArea resourceChart;

  private final EditMenu myEditMenu;

  private final ProjectMenu myProjectMenu;

  /**
   * Informations for the current project.
   */
  public PrjInfos prjInfos = new PrjInfos();

  /**
   * Boolean to know if the file has been modify
   */
  public boolean askForSave = false;

  private final ZoomActionSet myZoomActions;

  private UIConfiguration myUIConfiguration;

  private final GanttOptions options;

  private ArrayList<GanttPreviousState> myPreviousStates = new ArrayList<>();

  private final GanttChartTabContentPanel myGanttChartTabContent;

  private final ResourceChartTabContentPanel myResourceChartTabContent;

  private ParserFactory myParserFactory;

  private static Consumer<Boolean> ourQuitCallback = withSystemExit -> {
    if (withSystemExit) {
      System.exit(0);
    } else {
      System.err.println("Quit application was called without System.exit() request");
    }
  };

  private FXSearchUi mySearchUi;

  private final Supplier<GPAction> taskNewAction = Suppliers.memoize(myTaskActions::getCreateAction);
  private final Supplier<GPAction> resourceNewAction = Suppliers.memoize(myResourceActions::getResourceNewAction);
  private final Supplier<ArtefactAction> insertAction = Suppliers.memoize(() ->
    new ArtefactNewAction(
      () -> getViewManager().getActiveView().getCreateAction(),
      new Action[]{taskNewAction.get().asToolbarAction(), resourceNewAction.get().asToolbarAction()}
    )
  );


  public JMenuBar getMenuBar() {
    var bar = new JMenuBar();

    bar.add(myProjectMenu);
    bar.add(myEditMenu);

    ViewMenu viewMenu = new ViewMenu(getProject(), getViewManager(), getUiFacadeImpl().getDpiOption(), getUiFacadeImpl().getChartFontOption(), "view");
    bar.add(viewMenu);

    JMenu mTask = UIUtil.createTooltiplessJMenu(GPAction.createVoidAction("task"));
    mTask.add(myTaskActions.getCreateAction());
    mTask.add(myTaskActions.getPropertiesAction());
    mTask.add(myTaskActions.getDeleteAction());
    bar.add(mTask);

    JMenu mHuman = UIUtil.createTooltiplessJMenu(GPAction.createVoidAction("human"));
    ResourceActionSet resourceActionSet = myResourceActions;
    for (AbstractAction a : resourceActionSet.getActions()) {
      mHuman.add(a);
    }
    mHuman.add(resourceActionSet.getResourceSendMailAction());
    mHuman.add(resourceActionSet.getCloudResourceList());

    HelpMenu helpMenu = new HelpMenu(getProject(), getUIFacade(), getProjectUIFacade());
    bar.add(mHuman);
    bar.add(helpMenu.createMenu());
    return bar;
  }

  public GanttProject(Stage stage) {
    super(stage);
    LoggerApi<Logger> startupLogger = GPLogger.create("Window.Startup");
    startupLogger.debug("Creating main frame...");
    ToolTipManager.sharedInstance().setInitialDelay(200);
    ToolTipManager.sharedInstance().setDismissDelay(60000);

    getProjectImpl().getHumanResourceManager().addView(this);
    myCalendar.addListener(GanttProject.this::setModified);

    startupLogger.debug("1. loading look'n'feels");
    options = new GanttOptions(getRoleManager(), getDocumentManager(), false);
    myUIConfiguration = options.getUIConfiguration();
    myUIConfiguration.setChartFontOption(getUiFacadeImpl().getChartFontOption());
    myUIConfiguration.setDpiOption(getUiFacadeImpl().getDpiOption());

    addProjectEventListener(getTaskManager().getProjectListener());
    addProjectEventListener(getHumanResourceManager().getProjectListener());
    getActiveCalendar().addListener(getTaskManager().getCalendarListener());

    area = new GanttGraphicArea(this, getTaskManager(), getZoomManager(), getUndoManager(),
        myTaskTableChartConnector,
      Suppliers.memoize(() -> myTaskTableSupplier.get().getActionConnector())::get);
    resourceChart = new ResourceLoadGraphicArea(this, getZoomManager(), myResourceTableChartConnector);

    options.addOptionGroups(getUIFacade().getOptions());
    options.addOptionGroups(getUIFacade().getGanttChart().getOptionGroups());
    options.addOptionGroups(getUIFacade().getResourceChart().getOptionGroups());
    options.addOptionGroups(getProjectUIFacade().getOptionGroups());
    options.addOptionGroups(getDocumentManager().getNetworkOptionGroups());
    options.addOptions(GPCloudOptions.INSTANCE.getOptionGroup());
    options.addOptions(getRssFeedChecker().getOptions());
    options.addOptions(UpdateOptions.INSTANCE.getOptionGroup());
    options.addOptions(myTaskManagerConfig.getTaskOptions());
    startupLogger.debug("2. loading options");
    initOptions();

    getUIFacade().setLookAndFeel(getUIFacade().getLookAndFeel());
    getUiFacadeImpl().getAppFontOption().addChangeValueListener(event -> getGanttChart().reset());
    TreeTableCellsKt.initFontProperty(getUiFacadeImpl().getAppFontOption(), getUiFacadeImpl().getRowPaddingOption());
    TreeTableCellsKt.initColorProperties();
    getZoomManager().addZoomListener(area.getZoomListener());

    ScrollingManager scrollingManager = getScrollingManager();
    scrollingManager.addScrollingListener(area.getViewState());
    scrollingManager.addScrollingListener(resourceChart.getViewState());

    startupLogger.debug("3. creating menus...");
    myZoomActions = new ZoomActionSet(getZoomManager());
    myProjectMenu = new ProjectMenu(this, stage, "project");
    myEditMenu = new EditMenu(getProject(), getUIFacade(), getViewManager(), () -> mySearchUi.requestFocus(), "edit");


    startupLogger.debug("4. creating views...");

    myGanttChartTabContent = new GanttChartTabContentPanel(
        getProject(), getUIFacade(), area.getJComponent(),
        getUIConfiguration(), myTaskTableSupplier, myTaskActions, myUiInitializationPromise);

    myResourceChartTabContent = new ResourceChartTabContentPanel(getProject(), getUIFacade(),
      myResourceTableSupplier, resourceChart);
//++
//    addComponentListener(new ComponentAdapter() {
//      @Override
//      public void componentShown(ComponentEvent e) {
//        SwingUtilities.invokeLater(() -> {
//          getGanttChart().reset();
//          getResourceChart().reset();
//          // This will clear any modifications which might be caused by
//          // adjusting widths of table columns during initial layout process.
//          getProject().setModified(false);
//        });
//      }
//    });
    startupLogger.debug("5. calculating size and packing...");

    startupLogger.debug("6. changing language ...");
    languageChanged(null);
    // Add Listener after language update (to be sure that it is not updated
    // twice)
    language.addListener(this);

    startupLogger.debug("7. first attempt to restore bounds");
  //++
    //    addWindowListener(new WindowAdapter() {
//      @Override
//      public void windowClosing(WindowEvent windowEvent) {
//        quitApplication(true);
//      }
//
//      @Override
//      public void windowOpened(WindowEvent e) {
//        boundsLogger.debug("Resizing window...");
//        boundsLogger.debug("Bounds after opening: {}", new Object[]{GanttProject.this.getBounds()}, ImmutableMap.of());
//        restoreBounds();
//        // It is important to run aligners after look and feel is set and font sizes
//        // in the UI manager updated.
//        SwingUtilities.invokeLater(() -> {
//          for (RowHeightAligner aligner : myRowHeightAligners) {
//            aligner.optionsChanged();
//          }
//        });
//        getUiFacadeImpl().getDpiOption()
//            .addChangeValueListener(event -> SwingUtilities.invokeLater(() -> getContentPane().doLayout()));
//        getGanttChart().reset();
//        getResourceChart().reset();
//        // This will clear any modifications which might be caused by
//        // adjusting widths of table columns during initial layout process.
//        getProject().setModified(false);
//      }
//    });

    startupLogger.debug("8. finalizing...");
    // applyComponentOrientation(GanttLanguage.getInstance()
    // .getComponentOrientation());
    getProjectUIFacade().getProjectOpenActivityFactory().addListener(
      GanttProjectImplKt.createProjectModificationListener(this, getUIFacade())
    );
    //++addMouseListenerToAllContainer(this.getComponents());

    // Add globally available actions/key strokes
//    GPAction viewCycleForwardAction = new ViewCycleAction(getViewManager(), true);
//    UIUtil.pushAction(getTabs(), true, viewCycleForwardAction.getKeyStroke(), viewCycleForwardAction);
//
//    GPAction viewCycleBackwardAction = new ViewCycleAction(getViewManager(), false);
//    UIUtil.pushAction(getTabs(), true, viewCycleBackwardAction.getKeyStroke(), viewCycleBackwardAction);

    try {
      myObservableDocument.set(getDocumentManager().newUntitledDocument());
    } catch (IOException e) {
      gpLogger.error(Arrays.toString(e.getStackTrace()), new Object[]{}, ImmutableMap.of(), e);
    }
    DesktopIntegration.setup(GanttProject.this);
  }

  public WindowGeometry getWindowGeometry() {
    return new WindowGeometry(options.getX(), options.getY(), options.getWidth(), options.getHeight(), options.isMaximized());
  }

  public void setWindowGeometry(WindowGeometry value) {
    options.setWindowPosition((int)value.getLeftX(), (int)value.getTopY());
    options.setWindowSize((int)value.getWidth(), (int)value.getHeight(), value.isMaximized());
  }

  public List<GPAction> getAppLevelActions() {
    return List.of(insertAction.get(), getViewManager().getDeleteAction(), getViewManager().getPropertiesAction());
  }
  private void restoreBounds() {
    //++
    //    if (options.isLoaded()) {
//      if (options.isMaximized()) {
//        setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
//      }
//      Rectangle bounds = new Rectangle(options.getX(), options.getY(), options.getWidth(), options.getHeight());
//      boundsLogger.debug("Bounds stored in the  options: {}", new Object[]{bounds}, ImmutableMap.of());
//
//      UIUtil.MultiscreenFitResult fit = UIUtil.multiscreenFit(bounds);
//      // If more than 1/4 of the rectangle is visible on screen devices then leave it where it is
//      if (fit.totalVisibleArea < 0.25 || Math.max(bounds.width, bounds.height) < 100) {
//        // Otherwise if it is visible on at least one device, try to fit it there
//        if (fit.argmaxVisibleArea != null) {
//          bounds = fitBounds(fit.argmaxVisibleArea, bounds);
//        } else {
//          UIUtil.MultiscreenFitResult currentFit = UIUtil.multiscreenFit(this.getBounds());
//          if (currentFit.argmaxVisibleArea != null) {
//            // If there are no devices where rectangle is visible, fit it on the current device
//            bounds = fitBounds(currentFit.argmaxVisibleArea, bounds);
//          } else {
//            boundsLogger.debug(
//                "We have not found the display corresponding to bounds {}. Leaving the window where it is",
//                new Object[]{bounds}, ImmutableMap.of()
//            );
//            return;
//          }
//        }
//      }
//++      setBounds(bounds);
//    }
  }

//  static private Rectangle fitBounds(GraphicsConfiguration display, Rectangle bounds) {
//    Rectangle displayBounds = display.getBounds();
//    Rectangle visibleBounds = bounds.intersection(displayBounds);
//    int fitX = visibleBounds.x;
//    if (fitX + bounds.width > displayBounds.x + displayBounds.width) {
//      fitX = Math.max(displayBounds.x, displayBounds.x + displayBounds.width - bounds.width);
//    }
//    int fitY = visibleBounds.y;
//    if (fitY + bounds.height > displayBounds.y + displayBounds.height) {
//      fitY = Math.max(displayBounds.y, displayBounds.y + displayBounds.height - bounds.height);
//    }
//    return new Rectangle(fitX, fitY, bounds.width, bounds.height);
//
//  }


  private void initOptions() {
    options.setUIConfiguration(myUIConfiguration);
    options.load();
    myUIConfiguration = options.getUIConfiguration();
  }

//  private void addMouseListenerToAllContainer(Component[] containers) {
//    for (Component container : containers) {
//      container.addMouseListener(getStopEditingMouseListener());
//      if (container instanceof Container) {
//        addMouseListenerToAllContainer(((Container) container).getComponents());
//      }
//    }
//  }

//  /**
//   * @return A mouseListener that stop the edition in the ganttTreeTable.
//   */
//  private MouseListener getStopEditingMouseListener() {
//    if (myStopEditingMouseListener == null)
//      myStopEditingMouseListener = new MouseAdapter() {
//        // @Override
//        // public void mouseClicked(MouseEvent e) {
//        // if (e.getSource() != bNew && e.getClickCount() == 1) {
//        // tree.stopEditing();
//        // }
//        // if (e.getButton() == MouseEvent.BUTTON1
//        // && !(e.getSource() instanceof JTable)
//        // && !(e.getSource() instanceof AbstractButton)) {
//        // Task taskUnderPointer =
//        // area.getChartImplementation().findTaskUnderPointer(e.getX(),
//        // e.getY());
//        // if (taskUnderPointer == null) {
//        // getTaskSelectionManager().clear();
//        // }
//        // }
//        // }
//      };
//    return myStopEditingMouseListener;
//  }

  /**
   * @return the options of ganttproject.
   */
  public GanttOptions getGanttOptions() {
    return options;
  }

  /**
   * Function to change language of the project
   */
  @Override
  public void languageChanged(Event event) {
//++    applyComponentOrientation(language.getComponentOrientation());
    area.repaint();
    resourceChart.repaint();

//++    applyComponentOrientation(language.getComponentOrientation());
  }

  public GPCloudStatusBar createStatusBar() {
    var result  = new GPCloudStatusBar(
      myObservableDocument, getUIFacade(), getProjectUIFacade(), getProject()
    );
    result.getLockPanel().getStylesheets().add("biz/ganttproject/app/StatusBar.css");
    return result;
  }
  /**
   * Create the button on toolbar
   */
  public FXToolbarBuilder createToolbar() {
    FXToolbarBuilder builder = new FXToolbarBuilder();
    builder.addButton(myProjectMenu.getOpenProjectAction().asToolbarAction())
        .addButton(myProjectMenu.getSaveProjectAction().asToolbarAction())
        .addWhitespace();

    builder.addButton(taskNewAction.get().asToolbarAction()).addButton(resourceNewAction.get().asToolbarAction());
    builder.addButton(getViewManager().getDeleteAction().asToolbarAction());

    var propertiesAction = getViewManager().getPropertiesAction();

    //++UIUtil.registerActions(getRootPane(), false, newAction, propertiesAction, deleteAction);
    // TODO: it might be necessary to uncomment it
    //UIUtil.registerActions(myGanttChartTabContent.getComponent(), true, newAction, propertiesAction, deleteAction);
//    UIUtil.registerActions(myResourceChartTabContent.getComponent(), true, insertAction.get(), propertiesAction,
//      getViewManager().getDeleteAction());
//    getTabs().getModel().addChangeListener(e -> {
//      // Tell artefact actions that the active provider changed, so they
//      // are able to update their state according to the current delegate
//      newAction.actionStateChanged();
//      propertiesAction.actionStateChanged();
//      deleteAction.actionStateChanged();
//      getTabs().getSelectedComponent().requestFocus();
//    });

    builder
        .addWhitespace()
        .addButton(propertiesAction)
        .addButton(getCutAction().asToolbarAction())
        .addButton(getCopyAction().asToolbarAction())
        .addButton(getPasteAction().asToolbarAction())
        .addWhitespace()
        .addButton(myEditMenu.getUndoAction().asToolbarAction())
        .addButton(myEditMenu.getRedoAction().asToolbarAction());
    mySearchUi = new FXSearchUi(getProject(), getUIFacade(), myEditMenu.getSearchAction());
    builder.addSearchBox(mySearchUi);
    builder.withClasses("toolbar-common", "toolbar-main", "toolbar-big");
    //return result;
    getWindowOpenedBarrier().await(opened -> {
      if (opened) {
        insertAction.get().init();
//        deleteAction.get().init();
//        propertiesAction.init();
      }
      return Unit.INSTANCE;
    });
    return builder;
  }

  void doShow() {
    getRssFeedChecker().run();
  }

  @Override
  public @NotNull List<GanttPreviousState> getBaselines() {
    return myPreviousStates;
  }

  /**
   * Create a new project
   */
  public void newProject() {
    getProjectUIFacade().createProject(getProject());
  }

  @Override
  public void open(Document document) throws IOException, DocumentException {
    document.read();
    getDocumentManager().addToRecentDocuments(document);
    //myMRU.add(document.getPath(), true);
    myObservableDocument.set(document);
    updateTitle();
    refresh();
    getProjectImpl().fireProjectOpened();
  }

  /**
   * @return the UIConfiguration.
   */
  @Override
  public @NotNull UIConfiguration getUIConfiguration() {
    return myUIConfiguration;
  }

  private boolean myQuitEntered = false;

  /**
   * Quit the application
   */
  @Override
  public Barrier<Boolean> quitApplication(boolean withSystemExit) {
    if (myQuitEntered) {
      return new ResolvedBarrier<>(true);
    }
    myQuitEntered = true;
    try {
      options.setUIConfiguration(myUIConfiguration);
      options.save();
      var barrier = getProjectUIFacade().ensureProjectSaved(getProject());
      barrier.await(result -> {
        if (result) {
          getProject().close();
          doQuitApplication(withSystemExit);
        } else {
          //++setVisible(true);
        }
        return Unit.INSTANCE;
      });
      return barrier;
    } finally {
      myQuitEntered = false;
    }
  }

  public void setAskForSave(boolean afs) {
    getProjectImpl().fireProjectModified(afs, (ex) -> getUIFacade().showErrorDialog(ex) );
    askForSave = afs;
  }

  public GanttGraphicArea getArea() {
    return this.area;
  }

  public GPAction getCopyAction() {
    return getViewManager().getCopyAction();
  }

  public GPAction getCutAction() {
    return getViewManager().getCutAction();
  }

  public GPAction getPasteAction() {
    return getViewManager().getPasteAction();
  }

  @Override
  public ZoomActionSet getZoomActionSet() {
    return myZoomActions;
  }

  @Override
  public ViewProvider getGanttViewProvider() {
    return myGanttChartTabContent;
  }

  @Override
  public ViewProvider getResourceViewProvider() {
    return myResourceChartTabContent;
  }

  public static class Args {

    @Parameter(names = "-log", description = "Enable logging", arity = 1)
    public boolean log = true;

    @Parameter(names = "-log_file", description = "Log file name")
    public String logFile = "auto";

    @Parameter(names = {"-h", "-help"}, description = "Print usage")
    public boolean help = false;

    @Parameter(names = {"-version"}, description = "Print version number")
    public boolean version = false;

    @Parameter(names = "--fix-menu-bar-title", description = "Fixes the application title in the menu bar on Linux with Unity desktop environment")
    public boolean fixMenuBarTitle = false;

    @Parameter(description = "Input file name")
    public List<String> file = null;
  }

  // ///////////////////////////////////////////////////////
  // IGanttProject implementation
  @Override
  public @NotNull String getProjectName() {
    return prjInfos.getName();
  }

  @Override
  public void setProjectName(@NotNull String projectName) {
    prjInfos.setName(projectName);
    setAskForSave(true);
  }

  @Override
  public @NotNull String getDescription() {
    return Objects.requireNonNullElse(prjInfos.getDescription(), "");
  }

  @Override
  public void setDescription(@NotNull String description) {
    prjInfos.setDescription(description);
    setAskForSave(true);
  }

  @Override
  public @NotNull String getOrganization() {
    return prjInfos.getOrganization();
  }

  @Override
  public void setOrganization(@NotNull String organization) {
    prjInfos.setOrganization(organization);
    setAskForSave(true);
  }

  @Override
  public @NotNull String getWebLink() {
    return prjInfos.getWebLink();
  }

  @Override
  public void setWebLink(@NotNull String webLink) {
    prjInfos.setWebLink(webLink);
    setAskForSave(true);
  }

  @Override
  public @NotNull HumanResourceManager getHumanResourceManager() {
    return getProjectImpl().getHumanResourceManager();
  }

  @Override
  public @NotNull RoleManager getRoleManager() {
    return getProjectImpl().getRoleManager();
  }

  @Override
  public @NotNull Document getDocument() {
    return myObservableDocument.get();
  }

  @Override
  public void setDocument(@NotNull Document document) {
    myObservableDocument.set(document);
  }

  @Override
  public void setModified() {
    setModified(true);
  }

  @Override
  public void setModified(boolean modified) {
    setAskForSave(modified);
    updateTitle();
  }

  @Override
  public boolean isModified() {
    return askForSave;
  }

  @Override
  public void close() {
    getProjectImpl().fireProjectClosed();
    prjInfos = new PrjInfos();
    RoleManager.Access.getInstance().clear();
    getTaskCustomColumnManager().reset();
    getResourceCustomPropertyManager().reset();

    for (GanttPreviousState myPreviousState : myPreviousStates) {
      myPreviousState.remove();
    }
    myPreviousStates = new ArrayList<>();
    myCalendar.reset();
    //myFacadeInvalidator.projectClosed();
  }

  @Override
  protected ParserFactory getParserFactory() {
    if (myParserFactory == null) {
      myParserFactory = new ParserFactoryImpl();
    }
    return myParserFactory;
  }

  // ///////////////////////////////////////////////////////////////
  // ResourceView implementation
  @Override
  public void resourceAdded(ResourceEvent event) {
    setAskForSave(true);
  }

  @Override
  public void resourcesRemoved(ResourceEvent event) {
    setAskForSave(true);
  }

  @Override
  public void resourceChanged(ResourceEvent e) {
    setAskForSave(true);
  }

  @Override
  public void resourceAssignmentsChanged(ResourceEvent e) {
    setAskForSave(true);
  }

  @Override
  public void resourceStructureChanged() {
    setAskForSave(true);
  }

  @Override
  public void resourceModelReset() {
  }

  // ///////////////////////////////////////////////////////////////
  // UIFacade

  @Override
  public GanttChart getGanttChart() {
    return getArea();
  }

  @Override
  public TimelineChart getResourceChart() {
    return resourceChart;
  }

  private class ParserFactoryImpl implements ParserFactory {
    @Override
    public GPParser newParser() {

      return new GanttXMLOpen(prjInfos, getTaskManager(), getUIFacade());

    }

    @Override
    public GPSaver newSaver() {
      return new GanttXMLSaver(GanttProject.this, getArea(), getUIFacade(),
        myGanttChartTabContent,
        myResourceChartTabContent,
        () -> myTaskTableSupplier.get().getColumnList(), GanttProject.this::getTaskFilterManager,
        () -> myResourceTableSupplier.get().getColumnList());
    }
  }

  public static void setApplicationQuitCallback(Consumer<Boolean> callback) {
    ourQuitCallback = callback;
  }

  public static void doQuitApplication(boolean withSystemExit) {
    ourQuitCallback.accept(withSystemExit);
  }
  @Override
  public void refresh() {
    getViewManager().refresh();
    //++super.repaint();
  }

}

