package day8

import java.sql.Timestamp

import com.atguigu.apitest.SensorReading
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.{EnvironmentSettings, Over, Table, Tumble}
import org.apache.flink.table.api.scala._
import org.apache.flink.types.Row

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved 
  *
  * Project: FlinkTutorial
  * Package: day8
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/25 10:26
  */
object WindowTest {
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

    // 窗口操作
    // 1.1 Group 窗口，开一个10秒滚动窗口，统计每个传感器温度的数量
    val groupResultTable: Table = sensorTable
      .window( Tumble over 10.seconds on 'ts as 'tw )
      .groupBy('id, 'tw)
      .select('id, 'id.count, 'tw.end)

    // 1.2 Group窗口 SQL实现
    tableEnv.createTemporaryView("sensor", sensorTable)
    val groupResultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select
        |  id,
        |  count(id),
        |  tumble_end(ts, interval '10' second)
        |from sensor
        |group by
        |  id,
        |  tumble(ts, interval '10' second)
      """.stripMargin)

    // 2.1 Over窗口，对每个传感器统计每一行数据与前两行数据的平均温度
    val overResultTable: Table = sensorTable
      .window( Over partitionBy 'id orderBy 'ts preceding 2.rows as 'w )
      .select( 'id, 'ts, 'id.count over 'w, 'temperature.avg over 'w )

    // 2.2 Over窗口 SQL实现
    val overResultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id, ts,
        |  count(id) over w,
        |  avg(temperature) over w
        |from sensor
        |window w as (
        |  partition by id
        |  order by ts
        |  rows between 2 preceding and current row
        |)
      """.stripMargin)


    // 转换成流打印输出
//    groupResultTable.toRetractStream[(String, Long, Timestamp)].print("group result")
//    groupResultSqlTable.toAppendStream[Row].print("group sql result")
    overResultTable.toAppendStream[Row].print("over result")
    overResultSqlTable.toAppendStream[Row].print("over sql result")

    env.execute("time and window test")
  }
}
