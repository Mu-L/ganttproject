/*
Copyright 2012 GanttProject Team

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
package net.sourceforge.ganttproject.task.algorithm;

import biz.ganttproject.core.calendar.GPCalendar;
import biz.ganttproject.core.calendar.GPCalendar.DayMask;
import biz.ganttproject.core.calendar.GPCalendarCalc;
import biz.ganttproject.core.time.CalendarFactory;
import biz.ganttproject.core.time.GanttCalendar;
import biz.ganttproject.core.time.TimeDuration;
import biz.ganttproject.core.time.TimeUnit;
import com.google.common.collect.BoundType;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import net.sourceforge.ganttproject.GPLogger;
import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade;
import net.sourceforge.ganttproject.task.TaskImpl;
import net.sourceforge.ganttproject.task.TaskMutator;
import net.sourceforge.ganttproject.task.algorithm.DependencyGraph.DependencyEdge;
import net.sourceforge.ganttproject.task.algorithm.DependencyGraph.ImplicitSubSuperTaskDependency;
import net.sourceforge.ganttproject.task.algorithm.DependencyGraph.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * This class walk the dependency graph and updates start and end dates of tasks
 * according to information returned by dependency edges.
 *
 * @author dbarashev
 */
public class SchedulerImpl extends AlgorithmBase {
  private final DependencyGraph myGraph;
  private boolean isRunning;
  private final Supplier<TaskContainmentHierarchyFacade> myTaskHierarchy;

  public SchedulerImpl(DependencyGraph graph, Supplier<TaskContainmentHierarchyFacade> taskHierarchy) {
    myGraph = graph;
    myTaskHierarchy = taskHierarchy;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
  }

  @Override
  public void run() {
    if (!isEnabled() || isRunning) {
      return;
    }
    isRunning = true;
    try {
      doRun();
    } finally {
      isRunning = false;
    }
  }

  private void doRun() {
    int layers = myGraph.checkLayerValidity();
    for (int i = 0; i < layers; i++) {
      Collection<Node> layer = myGraph.getLayer(i);
      for (Node node : layer) {
        try {
          schedule(node);
        } catch (IllegalArgumentException e) {
          if (getDiagnostic() != null) {
            getDiagnostic().logError(e);
          } else {
            error(e);
          }
        }
      }
    }
  }

  private void schedule(Node node) {
    debug("Scheduling node {}", node);
    Range<Date> startRange = Range.all();
    Range<Date> endRange = Range.all();

    Range<Date> weakStartRange = Range.all();
    Range<Date> weakEndRange = Range.all();

    List<Date> subtaskRanges = Lists.newArrayList();
    List<DependencyEdge> incoming = node.getIncoming();
    debug(".. #incoming edges={}", incoming.size());
    for (DependencyEdge edge : incoming) {
      if (!edge.refresh()) {
        continue;
      }
      if (edge instanceof ImplicitSubSuperTaskDependency) {
        subtaskRanges.add(edge.getStartRange().upperEndpoint());
        subtaskRanges.add(edge.getEndRange().lowerEndpoint());
      } else {
        if (edge.isWeak()) {
          weakStartRange = weakStartRange.intersection(edge.getStartRange());
          weakEndRange = weakEndRange.intersection(edge.getEndRange());
        } else {
          startRange = startRange.intersection(edge.getStartRange());
          endRange = endRange.intersection(edge.getEndRange());
        }
      }
      if (startRange.isEmpty() || endRange.isEmpty()) {
        debug("..both start and end ranges were calculated as empty for task={} Skipping it", node.getTask());
      }
    }
    debug("..Ranges: start={} end={} weakStart={} weakEnd={}", startRange, endRange, weakStartRange, weakEndRange);

    Range<Date> subtasksSpan = subtaskRanges.isEmpty() ?
        Range.closed(node.getTask().getStart().getTime(), node.getTask().getEnd().getTime()) : Range.encloseAll(subtaskRanges);
    Range<Date> subtreeStartUpwards = subtasksSpan.span(Range.downTo(node.getTask().getStart().getTime(), BoundType.CLOSED));
    Range<Date> subtreeEndDownwards = subtasksSpan.span(Range.upTo(node.getTask().getEnd().getTime(), BoundType.CLOSED));
    debug("..Subtasks span={}", subtasksSpan);

    if (!startRange.equals(Range.all())) {
      startRange = startRange.intersection(weakStartRange);
    } else if (!weakStartRange.equals(Range.all())) {
      startRange = weakStartRange.intersection(subtreeStartUpwards);
    }
    if (!endRange.equals(Range.all())) {
      endRange = endRange.intersection(weakEndRange);
    } else if (!weakEndRange.equals(Range.all())) {
      endRange = weakEndRange.intersection(subtreeEndDownwards);
    }
    if (node.getTask().getThirdDateConstraint() == TaskImpl.EARLIESTBEGIN && node.getTask().getThird() != null) {
      startRange = startRange.intersection(Range.downTo(node.getTask().getThird().getTime(), BoundType.CLOSED));
      debug(".. applying earliest start={}. Now start range={}", node.getTask().getThird(), startRange);
    }
    if (!subtaskRanges.isEmpty()) {
      startRange = startRange.intersection(subtasksSpan);
      endRange = endRange.intersection(subtasksSpan);
    }
    debug(".. finally, start range={}", startRange);
    if (startRange.hasLowerBound()) {
      modifyTaskStart(node.getTask(), startRange.lowerEndpoint());
    }
    if (endRange.hasUpperBound()) {
      GPCalendarCalc cal = node.getTask().getManager().getCalendar();
      Date endDate = endRange.upperEndpoint();
      TimeUnit timeUnit = node.getTask().getDuration().getTimeUnit();
      if (DayMask.WORKING == (cal.getDayMask(endDate) & DayMask.WORKING)) {
        // in case if calculated end date falls on first day after holidays (say, on Monday)
        // we'll want to modify it a little bit, so that it falls on that holidays start
        // If we don't do this, it will be done automatically the next time task activities are recalculated,
        // and thus task end date will keep changing
        Date closestWorkingEndDate = cal.findClosest(
            endDate, timeUnit, GPCalendarCalc.MoveDirection.BACKWARD, GPCalendar.DayType.WORKING);
        Date closestNonWorkingEndDate = cal.findClosest(
            endDate, timeUnit, GPCalendarCalc.MoveDirection.BACKWARD, GPCalendar.DayType.NON_WORKING, closestWorkingEndDate);
        // If there is a non-working date between current task end and closest working date
        // then we're really just after holidays
        if (closestNonWorkingEndDate != null && closestWorkingEndDate.before(closestNonWorkingEndDate)) {
          // we need to adjust-right closest working date to position to the very beginning of the holidays interval
          Date nonWorkingPeriodStart = timeUnit.adjustRight(closestWorkingEndDate);
          if (nonWorkingPeriodStart.after(node.getTask().getStart().getTime())) {
            endDate = nonWorkingPeriodStart;
          }
        }
      }
      modifyTaskEnd(node.getTask(), endDate);
    }
  }

  private void modifyTaskEnd(Task task, Date newEnd) {
    if (task.getEnd().getTime().equals(newEnd)) {
      return;
    }
    GanttCalendar newEndCalendar = CalendarFactory.createGanttCalendar(newEnd);
    if (getDiagnostic() != null) {
      getDiagnostic().addModifiedTask(task, null, newEnd);
    }
    TaskMutator mutator = task.createMutator();
    mutator.setEnd(newEndCalendar);
    mutator.commit();
  }

  private void modifyTaskStart(Task task, Date newStart) {
    if (task.getStart().getTime().equals(newStart)) {
      return;
    }
    GanttCalendar newStartCalendar = CalendarFactory.createGanttCalendar(newStart);
    if (getDiagnostic() != null) {
      getDiagnostic().addModifiedTask(task, newStart, null);
    }

    if (myTaskHierarchy.get().hasNestedTasks(task)) {
      TaskMutator mutator = task.createMutator();
      mutator.setStart(newStartCalendar);
      mutator.commit();
    } else {
      var mutator = task.createShiftMutator();
      TimeDuration shift = task.getManager().createLength(task.getDuration().getTimeUnit(), task.getStart().getTime(), newStart);
      mutator.shift(shift);
      mutator.commit();
    }
  }

  private void debug(String message, Object... params) {
    GPLogger.create("SchedulerImpl").debug(message, params, Collections.emptyMap());
  }

  private void error(Exception ex) {
    GPLogger.create("SchedulerImpl").error(ex.getMessage(), new Object[0], Collections.emptyMap(), ex);
  }
}
