package com.atguigu.apitest.sinktest

import java.util

import com.atguigu.apitest.SensorReading
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.elasticsearch.{ElasticsearchSinkFunction, RequestIndexer}
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink
import org.apache.http.HttpHost
import org.elasticsearch.client.Requests

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest.sinktest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/18 10:54
  */
object EsSinkTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val inputStream = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })

    // 定义一个httpHosts
    val httpHosts = new util.ArrayList[HttpHost]()
    httpHosts.add(new HttpHost("hadoop102", 9200))
    // 定义一个 ElasticsearchSinkFunction
    val esSinkFunc = new ElasticsearchSinkFunction[SensorReading] {
      override def process(element: SensorReading, ctx: RuntimeContext, indexer: RequestIndexer): Unit = {
        // 包装写入es的数据
        val dataSource = new util.HashMap[String, String]()
        dataSource.put("sensor_id", element.id)
        dataSource.put("temp", element.temperature.toString)
        dataSource.put("ts", element.timestamp.toString)
        // 创建一个index request
        val indexRequest = Requests.indexRequest()
          .index("sensor_temp")
          .`type`("readingdata")
          .source(dataSource)
        // 用indexer发送 http请求
        indexer.add( indexRequest )
        println( element + " saved successfully")
      }
    }
    dataStream.addSink( new ElasticsearchSink.Builder[SensorReading](httpHosts, esSinkFunc).build() )

    env.execute("es sink test")
  }
}
