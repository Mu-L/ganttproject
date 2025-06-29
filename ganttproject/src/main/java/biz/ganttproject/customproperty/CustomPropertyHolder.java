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
package biz.ganttproject.customproperty;


import java.util.List;

public interface CustomPropertyHolder {
  List<CustomProperty> getCustomProperties();

  CustomProperty addCustomProperty(CustomPropertyDefinition definition, String defaultValueAsString) throws CustomColumnsException;
  void setValue(CustomPropertyDefinition def, Object value) throws CustomColumnsException;
  Object getValue(CustomPropertyDefinition def);

  CustomPropertyHolder EMPTY = new CustomPropertyHolder() {
    @Override
    public List<CustomProperty> getCustomProperties() {
      return List.of();
    }

    @Override
    public CustomProperty addCustomProperty(CustomPropertyDefinition definition, String defaultValueAsString) throws CustomColumnsException {
      return null;
    }

    @Override
    public void setValue(CustomPropertyDefinition def, Object value) throws CustomColumnsException {

    }

    @Override
    public Object getValue(CustomPropertyDefinition def) {
      return null;
    }
  };
}
