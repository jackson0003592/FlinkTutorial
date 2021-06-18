package com.atguigu.apitest

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011

import scala.util.Random

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/17 11:44
  */

// 输入数据的样例类
case class SensorReading( id: String, timestamp: Long, temperature: Double )

object SourceTest {
  def main(args: Array[String]): Unit = {
    // 创建执行环境
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 1. 从集合中读取数据
    val stream1: DataStream[SensorReading] = env.fromCollection( List(
      SensorReading("sensor_1", 1547718199, 35.8),
      SensorReading("sensor_6", 1547718201, 15.4),
      SensorReading("sensor_7", 1547718202, 6.7),
      SensorReading("sensor_10", 1547718205, 38.1),
      SensorReading("sensor_1", 1547718207, 37.2),
      SensorReading("sensor_1", 1547718212, 33.5),
      SensorReading("sensor_1", 1547718215, 38.1)
    ) )

//    env.fromElements(0, 1.1, "sdfe")

    // 2. 从文件中读取数据
  // val stream2: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")

    // 3. socket文本流
//    val stream3 = env.socketTextStream("hadoop102", 7777)

    // 4. 从 kafka读取数据
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "hadoop104:9092")
    //properties.setProperty("group.id", "ab")
    properties.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    properties.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    properties.setProperty("auto.offset.reset", "latest")
    val stream4 = env.addSource( new FlinkKafkaConsumer011[String]("b", new SimpleStringSchema(), properties) )

    // 5. 自定义source
    val stream5 = env.addSource( new MySensorSource() )

    // 打印输出
   // stream5.print("stream5")
    stream4.print("stream4")
    env.execute("source test job")
  }
}

// 实现一个自定义的 SourceFunction，自动生成测试数据
class MySensorSource() extends SourceFunction[SensorReading]{
  // 定义一个flag，表示数据源是否正常运行
  var running: Boolean = true

  override def cancel(): Unit = running = false

  // 随机生成 SensorReading数据
  override def run(ctx: SourceFunction.SourceContext[SensorReading]): Unit = {
    // 定义一个随机数发生器
    val rand = new Random()


    // 随机生成 10个传感器的温度值，并且不停在之前温度基础上更新（随机上下波动）
    // 首先生成 10个传感器的初始温度
    var curTemps = 1.to(10).map(
      i => ("sensor_" + i, 60 + rand.nextGaussian() * 20)
    )

    // 无限循环，生成随机数据流
      while(running){
        // 在当前温度基础上，随机生成微小波动
        curTemps = curTemps.map(
          data => (data._1, data._2 + rand.nextGaussian())
        )
        // 获取当前系统时间
        val curTs = System.currentTimeMillis()
        // 包装成样例类，用ctx发出数据
        curTemps.foreach(
          data => ctx.collect(SensorReading(data._1, curTs, data._2))
        )
        // 定义间隔时间
        Thread.sleep(1000L)
    }
  }
}