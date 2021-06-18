package com.atguigu.apitest

import org.apache.flink.api.common.functions.{MapFunction, ReduceFunction, RichMapFunction, RuntimeContext}
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/17 15:23
  */
object TransformTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val inputStreamFromFile: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")

    // 1. 基本转换操作
    val dataStream: DataStream[SensorReading] = inputStreamFromFile
      .map( data => {
        val dataArray = data.split(",")
        SensorReading( dataArray(0), dataArray(1).toLong, dataArray(2).toDouble )
      } )

    // 2. 分组滚动聚合
    val aggStream: DataStream[SensorReading] = dataStream
      .keyBy("id")
//      .keyBy( data => data.id )
//      .keyBy( new MyIDSelector() )
//      .min("temperature")    // 取当前sensor的最小温度值
//        .reduce(new MyReduce)
      .reduce( (curRes, newData) =>
      SensorReading(curRes.id, curRes.timestamp.max(newData.timestamp), curRes.temperature.min(newData.temperature))
    )    // 聚合出每个sensor的最大时间戳和最小温度值

    // 3. 分流
    val splitStream: SplitStream[SensorReading] = dataStream
      .split( data => {
        if( data.temperature > 30 )
          Seq("high")
        else
          Seq("low")
      } )
    val highTempStream: DataStream[SensorReading] = splitStream.select("high")
    val lowTempStream: DataStream[SensorReading] = splitStream.select("low")
    val allTempStream: DataStream[SensorReading] = splitStream.select("high", "low")

//    dataStream.print()
//    highTempStream.print("high")
//    lowTempStream.print("low")
//    allTempStream.print("all")

    // 4. 合流
    val warningStream: DataStream[(String, Double)] = highTempStream.map(
      data => (data.id, data.temperature)
//      new MyMapper
    )
    val connectedStreams: ConnectedStreams[(String, Double), SensorReading] = warningStream
      .connect(lowTempStream)
    val resultStream: DataStream[Object] = connectedStreams.map(
      warningData => (warningData._1, warningData._2, "high temp warning"),
      lowTempData => (lowTempData.id, "normal")
    )

    val unionStream: DataStream[SensorReading] = highTempStream.union(lowTempStream, allTempStream)

    resultStream.print("result")

    env.execute("transform test job")
  }
}

// 自定义函数类，key选择器
class MyIDSelector() extends KeySelector[SensorReading, String]{
  override def getKey(value: SensorReading): String = value.id
}

// 自定义函数类 ReduceFunction
class MyRepuce() extends ReduceFunction[SensorReading]{
  override def reduce(value1: SensorReading, value2: SensorReading): SensorReading = {
    SensorReading( value1.id, value1.timestamp.max(value2.timestamp), value1.temperature.min(value2.temperature) )
  }
}

// 自定义MapFunction
class MyMapper extends MapFunction[SensorReading, (String, Double)]{
  override def map(value: SensorReading): (String, Double) = (value.id, value.temperature)
}

class MyRichMapper extends RichMapFunction[SensorReading, Int]{
  override def open(parameters: Configuration): Unit = {}

//  getRuntimeContext.getIndexOfThisSubtask

  override def map(value: SensorReading): Int = value.timestamp.toInt

  override def close(): Unit = {}
}