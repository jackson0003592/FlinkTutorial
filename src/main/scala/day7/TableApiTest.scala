package day7

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, EnvironmentSettings, Table, TableEnvironment}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors._

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: day7
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/24 11:06
  */
object TableApiTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    //    val tableEnv = StreamTableEnvironment.create(env)

    // 1.创建表环境
    // 1.1 创建老版本的流查询环境
    val settings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useOldPlanner()
      .inStreamingMode()
      .build()
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env, settings)

    // 1.2 创建老版本的批式查询环境
    val batchEnv: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    val batchTableEnv: BatchTableEnvironment = BatchTableEnvironment.create(batchEnv)

    // 1.3 创建blink版本的流查询环境
    val bsSettings = EnvironmentSettings.newInstance()
      .useBlinkPlanner()
      .inStreamingMode()
      .build()
    val bsTableEnv = StreamTableEnvironment.create(env, bsSettings)

    // 1.4 创建blink版本的批式查询环境
    val bbSettings = EnvironmentSettings.newInstance()
      .useBlinkPlanner()
      .inBatchMode()
      .build()
    //    val bbTableEnv = TableEnvironment.create(bbSettings)

    // 2. 从外部系统读取数据，在环境中注册表
    // 2.1 连接到文件系统（Csv）
    val filePath = "E:\\FlinkTutorial\\src\\main\\resources\\sensor.txt"
    tableEnv.connect(new FileSystem().path(filePath))
      .withFormat(new Csv()) // 定义读取数据之后的格式化方法
      .withSchema(new Schema()
      .field("id", DataTypes.STRING())
      .field("timestamp", DataTypes.BIGINT())
      .field("temperature", DataTypes.DOUBLE())
    ) // 定义表结构
      .createTemporaryTable("inputTable") // 注册一张表

    // 2.2 连接到Kafka
    tableEnv.connect(new Kafka()
      .version("0.11")
      .topic("sensor")
      .property("bootstrap.servers", "hadoop102:9092")
      .property("zookeeper.connect", "hadoop102:2181")
    )
      .withFormat( new Csv() )
      .withSchema( new Schema()
        .field("id", DataTypes.STRING())
        .field("timestamp", DataTypes.BIGINT())
        .field("temperature", DataTypes.DOUBLE())
      )
      .createTemporaryTable("kafkaInputTable")

    // 3. 表的查询
    // 3.1 简单查询，过滤投影
    val sensorTable: Table = tableEnv.from("inputTable")
    val resultTable: Table = sensorTable
      .select('id, 'temperature)
      .filter('id === "sensor_1")
    // 3.2 SQL简单查询
    val resultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id, temperature
        |from inputTable
        |where id = 'sensor_1'
      """.stripMargin)

    // 3.3 简单聚合，统计每个传感器温度个数
    val aggResultTable: Table = sensorTable
      .groupBy('id)
      .select('id, 'id.count as 'count)
    // 3.4 SQL实现简单聚合
    val aggResultSqlTable: Table = tableEnv.sqlQuery("select id, count(id) as cnt from inputTable group by id")

    // 转换成流打印输出
//    val sensorTable: Table = tableEnv.from("kafkaInputTable")
    resultTable.toAppendStream[(String, Double)].print("result")
    aggResultSqlTable.toRetractStream[(String, Long)].print("agg")

    env.execute("table api test job")
  }
}
