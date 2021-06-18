package day8

import com.atguigu.apitest.SensorReading
import org.apache.commons.math3.geometry.spherical.oned.ArcsSet.Split
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.{EnvironmentSettings, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.functions.{AggregateFunction, TableFunction}
import org.apache.flink.types.Row

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved 
  *
  * Project: FlinkTutorial
  * Package: day8
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/25 11:51
  */
object TableFunctionTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 读取数据创建DataStream
    val inputStream: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    //    val inputStream = env.socketTextStream("localhost", 7777)
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[SensorReading](Time.seconds(1)) {
        override def extractTimestamp(element: SensorReading): Long = element.timestamp * 1000L
      })

    // 创建表环境
    val settings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useOldPlanner()
      .inStreamingMode()
      .build()
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env, settings)

    // 将DataStream转换成Table
    val sensorTable: Table = tableEnv.fromDataStream(dataStream, 'id, 'timestamp.rowtime as 'ts, 'temperature)

    // 创建一个UDF的实例
    val split = new Split("_")
    // 调用Table API，TableFunction使用的时候，需要调用joinLateral方法
    val resultTable = sensorTable
        .joinLateral( split('id) as ('word, 'length) )
      .select( 'id, 'ts, 'word, 'length )

    // SQL实现
    tableEnv.createTemporaryView("sensor", sensorTable)
    tableEnv.registerFunction("split", split)
    val resultSqlTable = tableEnv.sqlQuery(
      """
        |select id, ts, word, length
        |from
        |sensor, lateral table(split(id)) as splitid(word, length)
      """.stripMargin)

    resultTable.toAppendStream[Row].print("result")
    resultSqlTable.toAppendStream[Row].print("sql result")

    env.execute("table function test")
  }
}

// 实现一个自定义TableFunction，对一个String，输出用某个分隔符切分之后的(word, wordlength)
class Split( separator: String ) extends TableFunction[(String, Int)]{
  def eval(str: String): Unit ={
    str.split(separator).foreach(
      word => collect((word, word.length))
    )
  }
}

class MyAgg() extends AggregateFunction[Int, Double]{
  override def getValue(accumulator: Double): Int = ???

  override def createAccumulator(): Double = ???

  def accumulate( accumulator: Double, field: String ): Unit ={

  }
}
