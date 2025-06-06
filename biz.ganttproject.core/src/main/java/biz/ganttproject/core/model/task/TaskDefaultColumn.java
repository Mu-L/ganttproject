/*
GanttProject is an opensource project management tool. License: GPL3
Copyright (C) 2012 GanttProject Team

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
package biz.ganttproject.core.model.task;

import biz.ganttproject.core.table.ColumnList;
import biz.ganttproject.core.table.ColumnList.Column;
import biz.ganttproject.core.table.BuiltinColumn;
import biz.ganttproject.customproperty.CustomPropertyClass;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

/**
 * Enumeration of built-in task properties.
 *
 * @author dbarashev (Dmitry Barashev)
 */
public enum TaskDefaultColumn implements BuiltinColumn {
  TYPE(new ColumnList.ColumnStub("tpd0", null, false, -1, -1), Icon.class, "tableColType"),
  PRIORITY(new ColumnList.ColumnStub("tpd1", null, false, -1, 50), Icon.class, "tableColPriority"),
  INFO(new ColumnList.ColumnStub("tpd2", null, false, -1, -1), Icon.class, "tableColInfo", Functions.NOT_EDITABLE, true),
  NAME(new ColumnList.ColumnStub("tpd3", null, true, 0, 200), String.class, "tableColName"),
  BEGIN_DATE(new ColumnList.ColumnStub("tpd4", null, true, 1, 75), GregorianCalendar.class, "tableColBegDate", Functions.ALWAYS_EDITABLE, false),
  END_DATE(new ColumnList.ColumnStub("tpd5", null, true, 2, 75), GregorianCalendar.class, "tableColEndDate", Functions.ALWAYS_EDITABLE, false),
  DURATION(new ColumnList.ColumnStub("tpd6", null, false, -1, 50), Integer.class, "tableColDuration", null, false),
  COMPLETION(new ColumnList.ColumnStub("tpd7", null, false, -1, 50), Integer.class, "tableColCompletion"),
  COORDINATOR(new ColumnList.ColumnStub("tpd8", null, false, -1, 200), String.class, "tableColCoordinator", Functions.NOT_EDITABLE, false),
  PREDECESSORS(new ColumnList.ColumnStub("tpd9", null, false, -1, 200), String.class, "tableColPredecessors"),
  ID(new ColumnList.ColumnStub("tpd10", null, false, -1, 20), Integer.class, "tableColID", Functions.NOT_EDITABLE, false),
  OUTLINE_NUMBER(new ColumnList.ColumnStub("tpd11", null, false, 4, 20), String.class, "tableColOutline", Functions.NOT_EDITABLE, false),
  COST(new ColumnList.ColumnStub("tpd12", null, false, -1, 20), BigDecimal.class, "tableColCost"),
  RESOURCES(new ColumnList.ColumnStub("tpd13", null, false, -1, 20), String.class, "resources", Functions.NOT_EDITABLE, false),
  COLOR(new ColumnList.ColumnStub("tpd14", null, false, -1, 20), Color.class, "option.taskDefaultColor.label", null, true),
  NOTES(new ColumnList.ColumnStub("tpd15", null, true, -1, 20), String.class, "notes", null, true),
  ATTACHMENTS(new ColumnList.ColumnStub("tpd16", null, false, -1, 20), Icon.class, "webLink", null, true);

  private final boolean myIsIconified;

  public interface LocaleApi {
    String i18n(String key);
  }
  private static LocaleApi ourLocaleApi;

  public static void setLocaleApi(LocaleApi localeApi) {
    ourLocaleApi = localeApi;
  }

  private final Column myDelegate;
  private final Class<?> myValueClass;
  private Predicate<? extends Object> myIsEditablePredicate;
  private final String myNameKey;
  private Comparator<?> mySortComparator;

  TaskDefaultColumn(ColumnList.Column delegate, Class<?> valueClass, String nameKey) {
    this(delegate, valueClass, nameKey, Functions.ALWAYS_EDITABLE, false);
  }

  TaskDefaultColumn(ColumnList.Column delegate, Class<?> valueClass, String nameKey, Predicate<? extends Object> isEditable, boolean isIconified) {
    myDelegate = delegate;
    myValueClass = valueClass;
    myIsEditablePredicate= isEditable;
    myNameKey = nameKey;
    myIsIconified = isIconified;
  }

  public Comparator<?> getSortComparator() {
    return mySortComparator;
  }

  public void setSortComparator(Comparator<?> sortComparator) {
    mySortComparator = sortComparator;
  }

  @Override
  public @NotNull Column getStub() {
    return myDelegate;
  }

  public static List<Column> getColumnStubs() {
    List<Column> result = new ArrayList<Column>();
    for (TaskDefaultColumn dc : values()) {
      result.add(new ColumnList.ColumnStub(
          dc.myDelegate.getID(), dc.getName(),
          dc.myDelegate.isVisible(), dc.myDelegate.getOrder(), dc.myDelegate.getWidth()));
    }
    return result;
  }

  public static TaskDefaultColumn find(String id) {
    for (TaskDefaultColumn column : values()) {
      if (column.getStub().getID().equals(id)) {
        return column;
      }
    }
    return null;
  }

  public Class<?> getValueClass() {
    return myValueClass;
  }

  public <T> void setIsEditablePredicate(Predicate<T> predicate) {
    myIsEditablePredicate = predicate;
  }

  public <T> boolean isEditable(T task) {
    return myIsEditablePredicate == null || ((Predicate<T>) myIsEditablePredicate).test(task);
  }

  public boolean isIconified() {
    return myIsIconified;
  }

  public String getNameKey() {
    return myNameKey;
  }

  public String getName() {
    return ourLocaleApi == null ? getNameKey() : ourLocaleApi.i18n(getNameKey());
  }

  public CustomPropertyClass getCustomPropertyClass() {
    if (myValueClass == Integer.class) {
      return CustomPropertyClass.INTEGER;
    }
    if (myValueClass == BigDecimal.class) {
      return CustomPropertyClass.DOUBLE;
    }
    if (myValueClass == GregorianCalendar.class) {
      return CustomPropertyClass.DATE;
    }
    return CustomPropertyClass.TEXT;
  }

  public CustomPropertyClass getColumnClass() { return getCustomPropertyClass(); }
  public static class Functions {
    static Predicate<Object> NOT_EDITABLE = input -> false;
    static Predicate<Object> ALWAYS_EDITABLE = input -> true;

    public static Comparator<String> OUTLINE_NUMBER_COMPARATOR = (s1, s2) -> {
      try (var sc1 = new Scanner(s1).useDelimiter("\\."); var sc2 = new Scanner(s2).useDelimiter("\\.")) {
        while (sc1.hasNextInt() && sc2.hasNextInt()) {
          var diff = sc1.nextInt() - sc2.nextInt();
          if (diff != 0) {
            return Integer.signum(diff);
          }
        }
        if (sc1.hasNextInt()) {
          return 1;
        }
        if (sc2.hasNextInt()) {
          return -1;
        }
        return 0;
      }
    };
  }
}
