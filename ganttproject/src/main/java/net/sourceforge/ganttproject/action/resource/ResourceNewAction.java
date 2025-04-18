/*
GanttProject is an opensource project management tool.
Copyright (C) 2011 GanttProject Team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject.action.resource;

import net.sourceforge.ganttproject.gui.GanttDialogPerson;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.gui.UIUtil;
import net.sourceforge.ganttproject.resource.HumanResource;
import net.sourceforge.ganttproject.resource.HumanResourceManager;
import net.sourceforge.ganttproject.roles.RoleManager;
import net.sourceforge.ganttproject.storage.ProjectDatabase;
import net.sourceforge.ganttproject.task.TaskManager;

import java.awt.event.ActionEvent;

/**
 * Action connected to the menu item for insert a new resource
 */
public class ResourceNewAction extends ResourceAction {
  private final UIFacade myUIFacade;

  private final RoleManager myRoleManager;
  private final TaskManager myTaskManager;
  private final ProjectDatabase myProjectDatabase;

  public ResourceNewAction(HumanResourceManager hrManager, ProjectDatabase projectDatabase, RoleManager roleManager, TaskManager taskManager, UIFacade uiFacade) {
    super("resource.new", hrManager);
    myUIFacade = uiFacade;
    myRoleManager = roleManager;
    myTaskManager = taskManager;
    myProjectDatabase = projectDatabase;
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (calledFromAppleScreenMenu(event)) {
      return;
    }
    final HumanResource resource = getManager().newHumanResource();
    resource.setRole(myRoleManager.getDefaultRole());
    GanttDialogPerson dp = new GanttDialogPerson(getManager(), getManager().getCustomPropertyManager(), myTaskManager, myProjectDatabase, myUIFacade, resource, ()->{});
    dp.setVisible(true);
  }

  @Override
  public void updateAction() {
    super.updateAction();
  }

  @Override
  public ResourceNewAction asToolbarAction() {
    ResourceNewAction result = new ResourceNewAction(getManager(), myProjectDatabase, myRoleManager, myTaskManager, myUIFacade);
    result.setFontAwesomeLabel(UIUtil.getFontawesomeLabel(result));
    return result;
  }
}
