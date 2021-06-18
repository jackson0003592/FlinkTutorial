package day8

import com.atguigu.apitest.SensorReading
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.{EnvironmentSettings, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.functions.ScalarFunction
import org.apache.flink.types.Row

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved 
  *
  * Project: FlinkTutorial
  * Package: day8
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/25 11:35
  */
object ScalarFunctionTest {
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
    val hashCode = new HashCode(10)
    // 调用Table API
    val resultTable = sensorTable
      .select( 'id, 'ts, hashCode('id) )

    // SQL实现
    tableEnv.createTemporaryView("sensor", sensorTable)
    tableEnv.registerFunction("hashcode", hashCode)
    val resultSqlTable = tableEnv.sqlQuery("select id, ts, hashcode(id) from sensor")

    resultTable.toAppendStream[Row].print("result")
    resultSqlTable.toAppendStream[Row].print("sql result")

    env.execute("scalar function test")

  }
}

// 自定义标量函数
class HashCode( factor: Int ) extends ScalarFunction{
  // 必须要实现一个eval方法，它的参数是当前传入的字段，它的输出是一个Int类型的hash值
  def eval(str: String): Int = {
    str.hashCode * factor
  }
}