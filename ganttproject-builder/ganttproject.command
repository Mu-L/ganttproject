#!/bin/bash
# Copyright 2014 BarD Software s.r.o
# This script launches GanttProject. It can be symlinked and can be ran from
# any working directory

SCRIPT_FILE="$0"

# If we can write to /tmp/ganttproject-launcher.log then LOG_TEXT
# will be empty, and we'll write the launcher logs to the file
# Otherwise it will be not empty and will accumulate the
# logged information in memory.
LOG_TEXT=""
echo "" > /tmp/ganttproject-launcher.log || LOG_TEXT="----"

log() {
  if [ ! -z "$LOG_TEXT" ]; then
    LOG_TEXT="$LOG_TEXT\n$1";
  else
    echo $1 >> /tmp/ganttproject-launcher.log
  fi
  [ -z "$DEBUG_ARGS" ] || echo $1
}

trap 'print_log' ERR
trap 'print_log' EXIT

print_log() {
  if [ "$?" -eq "0" ]; then
    return;
  fi
  if [ ! -z "$LOG_TEXT" ]; then
    >&2 echo $LOG_TEXT
  else
    >&2 cat /tmp/ganttproject-launcher.log
  fi
}

find_ganttproject_home() {
  WORKING_DIR="$(pwd)"
  # We want to find the directory where the real script file resides.
  # If real file is symlinked (possibly many times) then we need to follow
  # symlinks until we reach the real script
  # After that we run pwd to get directory path
  cd "$(dirname "$SCRIPT_FILE")"
  SCRIPT_FILE="$(basename "$SCRIPT_FILE")"

  while [ -L "$SCRIPT_FILE" ]; do
    SCRIPT_FILE="$(readlink "$SCRIPT_FILE")"
    cd "$(dirname "$SCRIPT_FILE")"
    SCRIPT_FILE="$(basename "$SCRIPT_FILE")"
  done

  pwd
}
GP_HOME="$(find_ganttproject_home)"
if [ -z "$GP_HOME" ]; then
  echo "GanttProject home directory is not set. Please point GP_HOME environment variable to the directory with GanttProject files."
  exit 1
fi

USE_BUNDLED_RUNTIME=1
DEBUG_ARGS=""
APP_ARGS=()

while true; do
  case "$1" in
    # Debug will switch on some debugging output and will allow for connecting to Java
    # process with a debugger
    -d|--debug)
      case "$2" in
        +[:digit:])
          DEBUG_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,address=*:$2,suspend=y"
          shift 2
          ;;
        *)
          DEBUG_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,address=*:5005,suspend=y"
          shift 1
          ;;
      esac
      log "Debug arguments: $DEBUG_ARGS"
      ;;
    # This allows for specifying the path to Java Runtime instead of the default bundled Java Runtime
    -j|--java-home)
      USE_BUNDLED_RUNTIME=0
      if [ -d "$2" ]; then
        JAVA_HOME="$2"
        log "Using JAVA_HOME=$2"
        shift 2
      else
        log "This is not a directory: $2"
        exit 1
      fi
      ;;
    "")
      break;
      ;;
    *)
      APP_ARGS+=("$1")
      shift 1
      ;;
  esac
done

# Create log directory
GP_LOG_DIR="$HOME/.ganttproject.d/logs"
# Check if log dir is present (or create it)
if [ ! -d "$GP_LOG_DIR" ]; then
  if [ -e  "$GP_LOG_DIR" ]; then
    echo "File $GP_LOG_DIR exists and is not a directory. Please remove it and launch $SCRIPT_FILE again" >&2
    exit 1
  fi
  if ! mkdir -p "$GP_LOG_DIR" ; then
    echo "Could not create $GP_LOG_DIR directory. Is directory $HOME writable?" >&2
    exit 1
  fi
fi

# Create unique name for the application log file
LOG_FILE="$GP_LOG_DIR/.ganttproject-"$(date +%Y%m%d%H%M%S)".log"
if [ -e "$LOG_FILE" ] && [ ! -w "$LOG_FILE" ]; then
  echo "Log file $LOG_FILE is not writable" >2
  exit 1
fi

check_java() {
  JAVA_COMMAND=$1
  log  "Searching for Java in $JAVA_COMMAND"

  if [ ! -x "$JAVA_COMMAND" ]; then
    log "...missing or not executable"
    JAVA_COMMAND=""
    return 1
  fi

  VERSION="$( $JAVA_COMMAND -version 2>&1 | head -n 1)"
  log "...found $VERSION"
  [[ "$VERSION" =~ 17\.? ]] && return 0;
  [[ "$VERSION" =~ 18\.? ]] && return 0;
  [[ "$VERSION" =~ 19\.? ]] && return 0;
  [[ "$VERSION" =~ 20\.? ]] && return 0;
  [[ "$VERSION" =~ 21\.? ]] && return 0;
  [[ "$VERSION" =~ 22\.? ]] && return 0;
  [[ "$VERSION" =~ 23\.? ]] && return 0;
  log "...this seems to be an old Java Runtime (or maybe just too new)";
  JAVA_COMMAND=""
  return 1
}

find_java() {
  if [ ! -z "$JAVA_HOME" ]; then
    check_java "$JAVA_HOME/bin/java" && return 0;
  fi
  JAVA_COMMAND=$(which java)
  if [ "0" = "$?" ]; then
    check_java "$JAVA_COMMAND" && return 0;
  fi

  if [ -x /usr/libexec/java_home ]; then
    check_java "$(/usr/libexec/java_home)/bin/java" && return 0;
  fi

  if [ -x /usr/libexec/java_home ]; then
    check_java "$(/usr/libexec/java_home -v 1.8+)/bin/java" && return 0;
  fi

  for f in $(ls /Library/Java/JavaVirtualMachines/); do
    check_java "/Library/Java/JavaVirtualMachines/$f/Contents/Home/bin/java" && return 0;
  done;

  check_java "/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java" && return 0;
  check_java /Library/Java/Home/bin/java && return 0;

  check_java /System/Library/Frameworks/JavaVM.framework/Home/bin/java && return 0;
  report_java_not_found && exit 1;
}

report_java_not_found() {
  log "JavaVM executable not found.
  You may want to set the path to the root of your Java Runtime installation
  in JAVA_HOME environment variable or pass it to ganttproject in --java-home argument";
  if [ -z "$LOG_TEXT" ]; then
    LOG_TEXT="$(cat /tmp/ganttproject-launcher.log)"
  fi

  LOG_TEXT=$(echo "$LOG_TEXT" | sed s/\"/\\\\\"/g)
  osascript -e 'tell app "System Events" to display alert "Java Runtime not found" message "GanttProject cannot find a suitable Java Runtime.\n\nWhat we have tried:\n'"$LOG_TEXT"'\n\nYou can find this log in /tmp/ganttproject-launcher.log file\nProceed to http://docs.ganttproject.biz/user/troubleshooting-installation to learn how to fix this."'
}

# Create updates directory if not exists
USER_UPDATES_DIR="$HOME/.ganttproject.d/updates"
mkdir -p "$USER_UPDATES_DIR"

find_java

if [ ! -f "$GP_HOME/eclipsito.jar" ]; then
  log "Can't find the required Eclipsito library at $GP_HOME/eclipsito.jar"
  exit 1
fi
CLASSPATH="$CLASSPATH:$GP_HOME/eclipsito.jar:$GP_HOME/lib/slf4j-api-2.0.17.jar:$GP_HOME/lib/slf4j-jdk14-2.0.4.jar:$GP_HOME/lib/logback-core-1.5.18.jar:$GP_HOME/lib/logback-classic-1.5.18.jar:$GP_HOME"
export CLASSPATH
BOOT_CLASS=com.bardsoftware.eclipsito.Launch
MACOS_ARGS="-Dapple.laf.useScreenMenuBar=true -Dcom.apple.macos.useScreenMenuBar=true	-Dcom.apple.mrj.application.apple.menu.about.name=GanttProject -Xdock:name=GanttProject -Xdock:icon=ganttproject.icns"

log "JAVA_HOME=$JAVA_HOME"
log "JAVA_COMMAND=$JAVA_COMMAND"
log "GP_HOME=$GP_HOME"
log "user.dir=$(pwd)"

JAVA_EXPORTS="--add-exports javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED\
  --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED\
  --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED\
  --add-exports javafx.base/com.sun.javafx.logging=ALL-UNNAMED\
  --add-exports javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED\
  --add-exports javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED\
  --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED\
  --add-exports javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED\
  --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED\
  --add-opens java.desktop/sun.swing=ALL-UNNAMED"
"$JAVA_COMMAND" -Dgpcloud=prod -Dorg.jooq.no-logo=true -Xmx1024m $JAVA_EXPORTS -Duser.dir="$GP_HOME" -Dfile.encoding=UTF-8 $MACOS_ARGS $DEBUG_ARGS $BOOT_CLASS \
  --app net.sourceforge.ganttproject.GanttProject \
  --version-dirs "$GP_HOME"/plugins:~/.ganttproject.d/updates \
  -log true -log_file "$LOG_FILE" "${APP_ARGS[@]}"
