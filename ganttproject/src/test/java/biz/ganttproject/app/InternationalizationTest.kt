/*
Copyright 2020 BarD Software s.r.o

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
package biz.ganttproject.app

import javafx.beans.property.SimpleObjectProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Tests for internationalization code.
 *
 * @author dbarashev@bardsoftware.com
 */
class InternationalizationTest {

  @Test
  fun `test simple localizer`() {
    val i18n = DefaultLocalizer(
      currentTranslation = newTranslation(mapOf(
          "foo" to "I am Foo",
          "hello" to "Hello, {0}"
      ))
    )
    assertEquals("I am Foo", i18n.formatText("foo"))
    assertEquals("Hello, world", i18n.formatText("hello", "world"))
  }

  @Test
  fun `test key prefix`() {
    val rootLocalizer = DefaultLocalizer(
      currentTranslation = newTranslation(mapOf(
          "foo.hello" to "Hello, {0}"
      ))
    )
    val i18n = rootLocalizer.createWithRootKey("foo")
    assertEquals("Hello, world", i18n.formatText("hello", "world"))
  }

  @Test
  fun `fallback localizer`() {
    val fallbackLocalizer = DefaultLocalizer(
      currentTranslation = newTranslation(mapOf(
          "hello" to "Hello, {0}"
      ))
    )
    val i18n = DefaultLocalizer(baseLocalizer = fallbackLocalizer,
      currentTranslation = newTranslation(mapOf(
          "world" to "World!"
      ))
    )
    assertEquals("Hello, World!", i18n.formatText("hello", i18n.formatText("world")))
  }

  @Test
  fun `prefix and fallback`() {
    val rootLocalizer = DefaultLocalizer(
      currentTranslation = newTranslation(mapOf(
          "foo.hello" to "Hello, {0}",
          "world" to "World!",
          "foo.ganttproject" to "GanttProject!",
          "ganttproject" to "You are not expected to see this"
    )))

    val i18n = rootLocalizer.createWithRootKey("foo", baseLocalizer = rootLocalizer)
    assertEquals("Hello, World!", i18n.formatText("hello", i18n.formatText("world")))
    assertEquals("Hello, GanttProject!", i18n.formatText("hello", i18n.formatText("ganttproject")))
  }

  @Test fun `mapping localizer`() {
    val rootLocalizer = DefaultLocalizer(
      currentTranslation = newTranslation(mapOf(
        "foo.hello" to "Hello, {0}",
    )))
    val mappingLocalizer = MappingLocalizer(mapOf(
      "helloWorld" to { rootLocalizer.create("foo.hello") }
    )) {
      LocalizedString("Unknown String", DummyLocalizer)
    }

    assertEquals("Hello, Local World", mappingLocalizer.formatText("helloWorld", "Local World"))
    assertEquals("Unknown String", mappingLocalizer.formatText("helloUniverse"))
  }


}

private fun newTranslation(kv: Map<String, String?>) : SimpleObjectProperty<Translation?> {
  val translation = Translation(Locale.US, kv::get)
  return SimpleObjectProperty(translation)
}
