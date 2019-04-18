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

package com.datamountaineer.streamreactor.connect.converters.source

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.UUID

import com.datamountaineer.streamreactor.connect.config.base.const.TraitConfigConst
import com.datamountaineer.streamreactor.connect.converters.MsgKey
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.sksamuel.avro4s._
import io.confluent.connect.avro.AvroData
import io.confluent.kafka.schemaregistry.client.rest.entities
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.{AvroRuntimeException, Schema, SchemaBuilder}
import org.apache.kafka.common.utils.ByteBufferOutputStream
import org.codehaus.jackson.map.ObjectMapper
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.reflect.io.Path

class AvroConverterTest extends WordSpec with Matchers with BeforeAndAfterAll {
  private val topic = "topicA"
  private val sourceTopic = "somesource"
  private val folder = new File(UUID.randomUUID().toString)
  folder.mkdir()
  val path = Path(folder.getAbsolutePath)

  override def beforeAll() = {

  }

  override def afterAll() = {
    path.deleteRecursively()
  }

  private def initializeSchemaRegistryConverter(converter: AvroConverter, schema: Schema): Unit = {
    val configuration = WireMockConfiguration.options().dynamicPort()
    val wireMock = new WireMockServer(configuration)
    val confluentSchema = new entities.Schema(schema.getName, 1, 1, schema.toString)
    val schemaJsonResponse = new ObjectMapper().writeValueAsString(confluentSchema)
    wireMock.stubFor(WireMock.get(s"/subjects/${schema.getName}/versions/latest")
      .willReturn(WireMock.aResponse().withBody(schemaJsonResponse)))
    wireMock.start()
    converter.initialize(Map(
      TraitConfigConst.SCHEMA_REGISTRY_SUFFIX -> wireMock.baseUrl(),
      AvroConverter.SCHEMA_CONFIG -> s"$sourceTopic=${schema.getName}"
    ))
  }

  private def initializeConverter(converter: AvroConverter, schema: Schema): Unit = {
    def writeSchema(schema: Schema): File = {
      val schemaFile = Paths.get(folder.getName, UUID.randomUUID().toString)
      val bw = new BufferedWriter(new FileWriter(schemaFile.toFile))
      bw.write(schema.toString)
      bw.close()

      schemaFile.toFile
    }

    converter.initialize(Map(
      AvroConverter.SCHEMA_CONFIG -> s"$sourceTopic=${writeSchema(schema)}"
    ))

  }

  private def write(record: GenericRecord): Array[Byte] = {
    val byteBuffer = ByteBuffer.wrap(new Array(128))
    val writer = new SpecificDatumWriter[GenericRecord](record.getSchema)
    val encoder = EncoderFactory.get().directBinaryEncoder(new ByteBufferOutputStream(byteBuffer), null)

    writer.write(record, encoder)

    byteBuffer.flip()
    byteBuffer.array()
  }

  private def testConverterAvroSupport(converter: AvroConverter, avro: GenericRecord): Unit = {
    val sourceRecord = converter.convert(topic, sourceTopic, "1001", write(avro))
    sourceRecord.key() shouldBe MsgKey.getStruct(sourceTopic, "1001")
    sourceRecord.keySchema() shouldBe MsgKey.schema
    val avroData = new AvroData(4)
    sourceRecord.valueSchema() shouldBe avroData.toConnectSchema(avro.getSchema)
    sourceRecord.value() shouldBe avroData.toConnectData(avro.getSchema, avro).value()
  }


  "AvroConverter" should {
    "handle null payloads" in {
      val converter = new AvroConverter()
      val schema = SchemaBuilder.builder().stringType()
      initializeConverter(converter, schema)

      val sourceRecord = converter.convert(topic, sourceTopic, "100", null)

      sourceRecord.key() shouldBe null
      sourceRecord.keySchema() shouldBe null
      sourceRecord.value() shouldBe null
    }

    "support schema registry" in {
      val recordFormat = RecordFormat[Transaction]
      val transaction = Transaction("test", 2354.99, System.currentTimeMillis())
      val avro = recordFormat.to(transaction)
      val converter = new AvroConverter
      initializeSchemaRegistryConverter(converter, avro.getSchema)
      testConverterAvroSupport(converter, avro)
    }

    "throw an exception if it can't parse the payload" in {
      intercept[AvroRuntimeException] {
        val recordFormat = RecordFormat[Transaction]
        val transaction = Transaction("test", 2354.99, System.currentTimeMillis())
        val avro = recordFormat.to(transaction)

        val converter = new AvroConverter
        initializeConverter(converter, avro.getSchema)

        val sourceRecord = converter.convert(topic, sourceTopic, "1001", write(avro).map(b => (b + 1) % 255).map(_.toByte))

        sourceRecord.key() shouldBe null
        sourceRecord.keySchema() shouldBe null

        val avroData = new AvroData(4)

        sourceRecord.value() shouldBe avroData.toConnectData(avro.getSchema, avro).value()

        sourceRecord.valueSchema() shouldBe avroData.toConnectSchema(avro.getSchema)
      }
    }

    "handle avro records" in {
      val recordFormat = RecordFormat[Transaction]
      val transaction = Transaction("test", 2354.99, System.currentTimeMillis())
      val avro = recordFormat.to(transaction)
      val converter = new AvroConverter
      initializeConverter(converter, avro.getSchema)
      testConverterAvroSupport(converter, avro)
    }
  }
}


case class Transaction(id: String, amount: Double, timestamp: Long)
