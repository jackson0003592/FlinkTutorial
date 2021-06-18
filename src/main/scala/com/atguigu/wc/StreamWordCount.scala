package com.atguigu.wc

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala._

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved 
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.wc
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/15 14:06
  */

// 流处理 word count
object StreamWordCount {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
//    env.setParallelism(8)
//    env.disableOperatorChaining()

    // 从程序运行参数中读取hostname和port
    val params: ParameterTool = ParameterTool.fromArgs(args)
    val hostname: String = params.get("host")
    val port: Int = params.getInt("port")
    // 接受socket文本流
    val inputDataStream: DataStream[String] = env.socketTextStream(hostname, port)

    // 定义转换操作，word count
    val resultDataStream: DataStream[(String, Int)] = inputDataStream
      .flatMap(_.split(" "))   // 以空格分词，打散得到所有的word
      .filter(_.nonEmpty).slotSharingGroup("2")
      .map( (_, 1) ).disableChaining()  // 转换成(word, count)二元组
      .keyBy(0)  // 按照第一个元素分组
      .sum(1).startNewChain()  // 按照第二个元素求和

    resultDataStream.print().setParallelism(1)
    env.execute("stream word count job")
  }
}
