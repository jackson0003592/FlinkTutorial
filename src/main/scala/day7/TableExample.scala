package day7

import com.atguigu.apitest.SensorReading
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{Table, TableEnvironment}
import org.apache.flink.table.api.scala._

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: day7
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/24 9:44
  */
object TableExample {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 读取数据创建DataStream
//    val inputStream: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    val inputStream = env.socketTextStream("hadoop102", 7777)
    val dataStream: DataStream[SensorReading] = inputStream
      .map( data => {
        val dataArray = data.split(",")
        SensorReading( dataArray(0), dataArray(1).toLong, dataArray(2).toDouble )
      } )

    // 创建表执行环境
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env)

    // 基于数据流，转换成一张表，然后进行操作
    val dataTable: Table = tableEnv.fromDataStream(dataStream)
//    val dataTable2: Table = tableEnv.fromDataStream(dataStream2)

    // 调用Table API，得到转换结果
    val resultTable: Table = dataTable
      .select("id, temperature")
      .filter("id == 'sensor_1'")

    // 或者直接写sql得到转换结果
    tableEnv.createTemporaryView("dataTable", dataTable)
//    tableEnv.registerTable("dataTable", dataTable)
    val resultSqlTable: Table = tableEnv
      .sqlQuery("select id, temperature from dataTable where id = 'sensor_1'")

    // 转换回数据流，打印输出
    val resultStream: DataStream[(String, Double)] = resultSqlTable.toAppendStream[(String, Double)]
    resultStream.print("result sql")
//    resultTable.printSchema()

    env.execute("table example job")
  }
}
