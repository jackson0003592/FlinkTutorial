package day7

import com.atguigu.apitest.SensorReading
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, EnvironmentSettings, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors.{Csv, FileSystem, Schema}

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: day7
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/24 14:41
  */

// 输出表计算的结果到文件
object OutputTableTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 读取数据创建DataStream
    val inputStream: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    //    val inputStream = env.socketTextStream("hadoop102", 7777)
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })

    // 创建表环境
    val settings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useOldPlanner()
      .inStreamingMode()
      .build()
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env, settings)

    // 将DataStream转换成Table
    val sensorTable: Table = tableEnv.fromDataStream(dataStream, 'id, 'temperature as 'temp, 'timestamp as 'ts)
    // 对Table进行转换操作，得到结果表
    val resultTable: Table = sensorTable
      .select('id, 'temp)
      .filter('id === "sensor_1")

    val aggResultTable: Table = sensorTable
      .groupBy('id)
      .select('id, 'id.count as 'count)

    // 定义一张输出表，这就是要写入数据的TableSink
    tableEnv.connect( new FileSystem().path("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\out.txt") )
      .withFormat( new Csv() )
      .withSchema( new Schema()
          .field("id", DataTypes.STRING())
          .field("cnt", DataTypes.BIGINT())
      )
      .createTemporaryTable("outputTable")

    // 将结果表写入table sink
    resultTable.insertInto("outputTable")

    //    sensorTable.printSchema()
    //    sensorTable.toAppendStream[(String, Double, Long)].print()
    env.execute("output table test")
  }
}
