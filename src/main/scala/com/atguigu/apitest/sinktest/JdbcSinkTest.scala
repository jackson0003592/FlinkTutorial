package com.atguigu.apitest.sinktest

import java.sql.{Connection, DriverManager, PreparedStatement}

import com.atguigu.apitest.{MySensorSource, SensorReading}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.scala._

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest.sinktest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/18 11:29
  */
object JdbcSinkTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

//    val inputStream = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    val inputStream = env.addSource( new MySensorSource() )
    val dataStream: DataStream[SensorReading] = inputStream
//      .map(data => {
//        val dataArray = data.split(",")
//        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
//      })

    dataStream.addSink( new MyJdbcSink() )

    env.execute("jdbc sink test")
  }
}

// 自定义一个 SinkFunction
class MyJdbcSink() extends RichSinkFunction[SensorReading]{
  // 首先定义sql连接，以及预编译语句
  var conn: Connection = _
  var insertStmt: PreparedStatement = _
  var updateStmt: PreparedStatement = _

  // 在open生命周期方法中创建连接以及预编译语句
  override def open(parameters: Configuration): Unit = {
    conn = DriverManager.getConnection("jdbc:mysql://hadoop102:3306/test", "root", "123456")
    insertStmt = conn.prepareStatement("insert into temp (sensor, temperature) values (?,?)")
    updateStmt = conn.prepareStatement("update temp set temperature = ? where sensor = ?")
  }

  override def invoke(value: SensorReading, context: SinkFunction.Context[_]): Unit = {
    // 执行更新语句
    updateStmt.setDouble(1, value.temperature)
    updateStmt.setString(2, value.id)
    updateStmt.execute()
    // 如果刚才没有更新数据，那么执行插入操作
    if( updateStmt.getUpdateCount == 0 ){
      insertStmt.setString(1, value.id)
      insertStmt.setDouble(2, value.temperature)
      insertStmt.execute()
    }
  }
  // 关闭操作
  override def close(): Unit = {
    insertStmt.close()
    updateStmt.close()
    conn.close()
  }
}