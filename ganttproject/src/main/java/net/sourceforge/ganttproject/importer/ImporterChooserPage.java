/*
Copyright 2005-2012 GanttProject Team

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
package net.sourceforge.ganttproject.importer;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;
import org.osgi.service.prefs.Preferences;

import biz.ganttproject.core.option.GPOptionGroup;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.gui.options.GPOptionChoicePanel;
import net.sourceforge.ganttproject.language.GanttLanguage;

/**
 * @author bard
 */
class ImporterChooserPage implements net.sourceforge.ganttproject.gui.projectwizard.WizardPage {
  private final List<Importer> myImporters;
  private final UIFacade myUiFacade;
  private final Preferences myPrefs;
  private final ImporterWizardModel myWizardState;

  ImporterChooserPage(@NotNull List<Importer> importers, UIFacade uiFacade, Preferences preferences, @NotNull ImporterWizardModel wizardState) {
    myImporters = importers;
    myUiFacade = uiFacade;
    myPrefs = preferences;
    myWizardState = wizardState;
  }

  @Override
  public String getTitle() {
    return GanttLanguage.getInstance().getText("importerChooserPageTitle");
  }

  @Override
  public JComponent getComponent() {
    Action[] choiceChangeActions = new Action[myImporters.size()];
    GPOptionGroup[] choiceOptions = new GPOptionGroup[myImporters.size()];
    for (int i = 0; i < myImporters.size(); i++) {
      final Importer importer = myImporters.get(i);
      final int index = i;
      Action nextAction = new AbstractAction(importer.getFileTypeDescription()) {
        @Override
        public void actionPerformed(ActionEvent e) {
          //onSelectImporter(importer);
        }
      };
      choiceChangeActions[i] = nextAction;
      choiceOptions[i] = null;
    }
    GPOptionChoicePanel panel = new GPOptionChoicePanel();
    panel.selectedIndexProperty.addListener((observable, oldValue, newValue) ->
      onSelectImporter(myImporters.get(newValue.intValue()))
    );
    return panel.getComponent(choiceChangeActions, choiceOptions, 0);
  }

  protected void onSelectImporter(Importer importer) {
    myWizardState.setImporter(importer);
  }

  @Override
  public void setActive(boolean b) {
//    myWizard = wizard;
//    if (wizard != null) {
//      onSelectImporter(myImporters.get(mySelectedIndex));
//    }
  }


}
