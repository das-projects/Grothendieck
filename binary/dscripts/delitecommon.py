# functionality common to delite and delitec

import os, sys
import ConfigParser

USER_HOME = os.getenv("HOME")
JAVA_HOME = os.getenv("JAVA_HOME")
MESOS_NATIVE_LIBRARY = os.getenv("MESOS_NATIVE_LIBRARY")
DELITE_MEM = os.getenv("DELITE_MEM")

script_path = os.path.dirname(__file__)
script_home = os.path.split(script_path)[0]
DIST_HOME = script_home

scala_major_id = "scala-2.10"

props = {}

def err(s):
    exit("error: " + s)

def warn(s):
    print("warn: " + s)
 
def initialize():
    loadProps()
    checkCommonEnv()

def loadProps():
    script_path = os.path.dirname(__file__)
    cand_home = os.path.split(script_path)[0]
    propf = cand_home + "/delite.properties"
    if not os.path.isfile(propf):
        return

    config = ConfigParser.ConfigParser()
    config.readfp(open(propf))
    items = config.items('delite')
    for item in items:
        k, v = item
        props[k] = v


def checkCommonEnv():
    global USER_HOME
    global JAVA_HOME
    global MESOS_NATIVE_LIBRARY
    global DELITE_MEM

    if JAVA_HOME is None:
        if "java.home" in props:
            JAVA_HOME = props["java.home"]
        else:
            # try to find it
            unix_java = "/usr/lib/jvm/default-java/"
            osx_java = "/Library/Java/Home/"

            if os.path.isdir(unix_java):
                JAVA_HOME = unix_java
            elif os.path.isdir(osx_java):
                JAVA_HOME = osx_java
            else:
                err("The JAVA_HOME environment variable must be defined or the java.home entry in delite.properties must be set.")

    if MESOS_NATIVE_LIBRARY is None:
        if "mesos.lib" in props:
            MESOS_NATIVE_LIBRARY = props["mesos.lib"]

    if DELITE_MEM is None:
        if "delite.mem" in props:
            DELITE_MEM = props["delite.mem"]

def printEnv():
  print("======== REQUIRED DELITE ENVIRONMENT VARIABLES =========")
  print("USER_HOME = " + USER_HOME)
  print("JAVA_HOME = " + JAVA_HOME)
