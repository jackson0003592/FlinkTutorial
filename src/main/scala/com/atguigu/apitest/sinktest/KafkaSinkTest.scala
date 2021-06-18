package com.atguigu.apitest.sinktest

import java.util.Base64.Encoder
import java.util.Properties

import com.atguigu.apitest.SensorReading
import org.apache.flink.api.common.serialization.{SimpleStringEncoder, SimpleStringSchema}
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}


/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest.sinktest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/18 9:27
  */
object KafkaSinkTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

//    val inputStream = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
val properties = new Properties()
    properties.setProperty("bootstrap.servers", "hadoop102:9092")
    properties.setProperty("group.id", "consumer-group")
    properties.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    properties.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    properties.setProperty("auto.offset.reset", "latest")
    val inputStream = env.addSource( new FlinkKafkaConsumer011[String]("sensor", new SimpleStringSchema(), properties) )
    val dataStream: DataStream[String] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble).toString
      })

    //    dataStream.writeAsCsv("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\out.txt")
    //    dataStream.addSink(
    //      StreamingFileSink.forRowFormat(
    //        new Path("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\out.txt"),
    //        new SimpleStringEncoder[String]("UTF-8"))
    //      .build() )
    inputStream.print()
    dataStream.addSink(new FlinkKafkaProducer011[String]("hadoop102:9092", "sinkTest", new SimpleStringSchema()))

    env.execute("kafka sink test")
  }
}
