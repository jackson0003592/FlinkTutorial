package day7

import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, EnvironmentSettings, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors.{Csv, Kafka, Schema}

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: day7
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/24 15:47
  */
object KafkaTableTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    val settings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useOldPlanner()
      .inStreamingMode()
      .build()
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env, settings)

    // 从kafka读取数据
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

    // 做转换操作
    // 对Table进行转换操作，得到结果表
    val sensorTable: Table = tableEnv.from("kafkaInputTable")
    val resultTable: Table = sensorTable
      .select('id, 'temperature)
      .filter('id === "sensor_1")

    val aggResultTable: Table = sensorTable
      .groupBy('id)
      .select('id, 'id.count as 'count)

    // 定义一个连接到kafka的输出表
    tableEnv.connect(new Kafka()
      .version("0.11")
      .topic("sinkTest")
      .property("bootstrap.servers", "hadoop102:9092")
      .property("zookeeper.connect", "hadoop102:2181")
    )
      .withFormat( new Csv() )
      .withSchema( new Schema()
        .field("id", DataTypes.STRING())
        .field("temp", DataTypes.DOUBLE())
      )
      .createTemporaryTable("kafkaOutputTable")
    // 将结果表输出
    resultTable.insertInto("kafkaOutputTable")


    val table = tableEnv.sqlQuery("select id,temp from kafkaOutputTable")
    table.toAppendStream[(String,Double)].print("test")
    env.execute("kafka table test")
  }
}
