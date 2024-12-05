/*
 * Copyright 2024 BarD Software s.r.o., Dmitry Barashev.
 *
 * This file is part of GanttProject, an opensource project management tool.
 *
 * GanttProject is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * GanttProject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package biz.ganttproject.storage

import biz.ganttproject.customproperty.*
import biz.ganttproject.storage.db.tables.Task
import net.sourceforge.ganttproject.TestSetupHelper
import net.sourceforge.ganttproject.resource.HumanResourceManager
import net.sourceforge.ganttproject.storage.*
import net.sourceforge.ganttproject.task.CostStub
import net.sourceforge.ganttproject.task.TaskManager
import org.h2.jdbcx.JdbcDataSource
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.w3c.util.DateParser
import java.math.BigDecimal
import java.sql.SQLException
import java.util.*
import javax.sql.DataSource

class CalculatedPropertyTest {
  private lateinit var dataSource: DataSource
  private lateinit var projectDatabase: ProjectDatabase
  private lateinit var taskManager: TaskManager
  private lateinit var customPropertyManager: CustomPropertyManager

  @BeforeEach
  fun init() {
    dataSource = JdbcDataSource().also {
      it.setURL("jdbc:h2:mem:test$SQL_PROJECT_DATABASE_OPTIONS")
    }
    projectDatabase = SqlProjectDatabaseImpl(dataSource)
    projectDatabase.init()

    taskManager = TestSetupHelper.newTaskManagerBuilder().also {
      it.setTaskUpdateBuilderFactory { task -> projectDatabase.createTaskUpdateBuilder(task) }
    }.build()

    customPropertyManager = taskManager.customPropertyManager
  }

  @AfterEach
  fun clear() {
    dataSource.connection.use { conn ->
      conn.createStatement().execute("shutdown")
    }
  }

  @Test
  fun `create task data table`() {
    customPropertyManager.createDefinition(CustomPropertyClass.TEXT, "foo")
    customPropertyManager.createDefinition(CustomPropertyClass.TEXT, "bar").also {
      it.calculationMethod = SimpleSelect(it.id, "tpc0 + '--'", resultClass = CustomPropertyClass.TEXT.javaClass)
    }
    rebuildTaskDataTable(dataSource, customPropertyManager)

    val dsl = DSL.using(dataSource, SQLDialect.H2)
    val tasks = dsl.selectFrom(Task.TASK).fetch()
    assert(tasks.isEmpty())
  }

  @Test
  fun `calculated property value`() {
    val foo = customPropertyManager.createDefinition(CustomPropertyClass.TEXT, "foo")
    val bar = customPropertyManager.createDefinition(CustomPropertyClass.INTEGER, "bar").also {
      it.calculationMethod = SimpleSelect(it.id, "duration + 1", resultClass = CustomPropertyClass.INTEGER.javaClass)
    }
    val baz = customPropertyManager.createDefinition(CustomPropertyClass.TEXT, "baz").also {
      it.calculationMethod = SimpleSelect(it.id, "tpc0 || '--'", resultClass = CustomPropertyClass.TEXT.javaClass)
    }
    rebuildTaskDataTable(dataSource, customPropertyManager)

    val task = taskManager.newTaskBuilder().withName("task1").withStartDate(Date()).build()
    task.customValues.setValue(foo, "foo")
    projectDatabase.insertTask(task)

    val dsl = DSL.using(dataSource, SQLDialect.H2)
    val tasks = dsl.selectFrom(Task.TASK).fetch()
    assertEquals(1, tasks.size)

    val propertyHolders = createPropertyHolders(taskManager)
    val updater = CalculatedPropertyUpdater(projectDatabase, {customPropertyManager}, {propertyHolders})
    updater.update()

    assertEquals(2, task.customValues.getValue(bar))
    assertEquals("foo--", task.customValues.getValue(baz))
  }

  @Test
  fun `builtin calculated property value`() {
    val task = taskManager.newTaskBuilder().withName("task1").withStartDate(Date()).build()
    projectDatabase.insertTask(task)

    H2Functions.taskManager.set(taskManager)
    task.createMutator().let {
      it.setCost(CostStub(BigDecimal.ZERO, true))
      it.setDuration(taskManager.createLength(2))
      it.commit()
    }

    val updater = CalculatedPropertyUpdater(projectDatabase, {customPropertyManager}, { emptyMap() })
    updater.update()

    DSL.using(dataSource, SQLDialect.H2).also {
      val tasks = it.selectFrom(Task.TASK).fetch()
      assertEquals(1, tasks.size)
      assertEquals(task.end.time, DateParser.toJavaDate(tasks[0].endDate))
    }

    val humanResourceManager = HumanResourceManager(null, CustomColumnsManager())
    val resource = humanResourceManager.newResourceBuilder().withName("foo").withID(1).withStandardRate(BigDecimal.valueOf(100)).build()
    task.assignmentCollection.addAssignment(resource).also {
        it.load = 100f;
    }

    updater.update()
    DSL.using(dataSource, SQLDialect.H2).also {
      val tasks = it.selectFrom(Task.TASK).fetch()
      assertEquals(1, tasks.size)
      assertEquals(task.cost.value.toDouble(), tasks[0].cost.toDouble())
      assertEquals(200.0, tasks[0].cost.toDouble())
    }

  }

  @Test
  fun `column used in a generated column can't be dropped`() {
    customPropertyManager.createDefinition(CustomPropertyClass.INTEGER, "bar").also {
      it.calculationMethod = SimpleSelect(it.id, "duration + 1", resultClass = CustomPropertyClass.INTEGER.javaClass)
    }
    rebuildTaskDataTable(dataSource, customPropertyManager)

    assertThrows<SQLException> {
      dataSource.connection.use { conn ->
        conn.createStatement().use {
          it.execute("ALTER TABLE Task DROP COLUMN duration;")
        }
        conn.commit()
      }
    }
  }

  @Test
  fun `custom column creation order`() {
    val manager = SqlCustomPropertyStorageManager(dataSource)
    customPropertyManager.createDefinition(CustomPropertyClass.INTEGER, "bar").also {
      it.calculationMethod = SimpleSelect(it.id, "tpc1 + 1", resultClass = it.type)
    }
    customPropertyManager.createDefinition(CustomPropertyClass.INTEGER, "foo")
    // We expect that the stored column tpc1 will be created first.
    manager.onCustomColumnChange(customPropertyManager)

    customPropertyManager.createDefinition(CustomPropertyClass.TEXT, "baz").also {
      it.calculationMethod = SimpleSelect(it.id, "'#' || tpc1", resultClass = it.type)
    }
    manager.onCustomColumnChange(customPropertyManager)
  }
}

private fun createPropertyHolders(taskManager: TaskManager) =
  HashMap<Int, CustomPropertyHolder>().also { mapping ->
    for (t in taskManager.getTasks()) {
      mapping[t.taskID] = t.customValues
    }
  }
