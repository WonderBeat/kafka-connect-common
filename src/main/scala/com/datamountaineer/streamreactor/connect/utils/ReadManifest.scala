/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.utils

import java.io.File
import java.util.jar.JarFile

import com.datamountaineer.streamreactor.connect.utils.JarManifest.getClass

import scala.collection.mutable

/**
  * Created by andrew@datamountaineer.com on 01/11/2017.
  * lenses-sql-runners
  */

case class JarManifest() {

  val map = mutable.Map.empty[String, String]

  var msg = "unknown"
  try {
    val file = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    if (file.isFile) {
      val jarFile = new JarFile(file)
      val manifest = jarFile.getManifest
      val attributes = manifest.getMainAttributes
      map += "StreamReactor-Version" -> attributes.getValue("StreamReactor-Version")
      map += "Kafka-Version" -> attributes.getValue("Kafka-Version")
      map += "Git-Repo" -> attributes.getValue("Git-Repo")
      map += "Git-Commit-Hash" -> attributes.getValue("Git-Commit-Hash")
      map += "Git-Tag" -> attributes.getValue("Git-Tag")
      map += "StreamReactor-Docs" -> attributes.getValue("StreamReactor-Docs")
    }
  }
  catch {
    case t: Throwable => msg = t.getMessage
  }


  def version(): String = map.getOrElse("StreamReactor-Version", "")

  def printManifest(): String = {
    var msg = "unknown"
    s"""
       |StreamReactor-Version:       ${map.getOrElse("StreamReactor-Version", msg)}
       |Kafka-Version:               ${map.getOrElse("Kafka-Version", msg)}
       |Git-Repo:                    ${map.getOrElse("Git-Repo", msg)}
       |Git-Commit-Hash:             ${map.getOrElse("Git-Commit-Hash", msg)}
       |Git-Tag:                     ${map.getOrElse("Git-Tag", msg)}
       |StreamReactor-Docs:          ${map.getOrElse("StreamReactor-Docs", msg)}
      """.
      stripMargin
  }
}