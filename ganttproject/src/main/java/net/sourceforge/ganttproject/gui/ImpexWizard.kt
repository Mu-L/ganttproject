package net.sourceforge.ganttproject.gui

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.File

internal fun basicValidateFile(file: File?): Result<File, String> {
  if (file == null) {
    return Err("File does not exist");
  }
  if (!file.exists()) {
    return Err("File does not exist")
  }
  if (!file.canRead()) {
    return Err("File read error");
  }
  return Ok(file)
}