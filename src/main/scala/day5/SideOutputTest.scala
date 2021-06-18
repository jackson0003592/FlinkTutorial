package day5

import com.atguigu.apitest.SensorReading
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: day5
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/21 10:17
  */
object SideOutputTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val inputStream = env.socketTextStream("hadoop102", 7777)
    val dataStream: DataStream[SensorReading] = inputStream
      .map( data => {
        val dataArray = data.split(",")
        SensorReading( dataArray(0), dataArray(1).toLong, dataArray(2).toDouble )
      } )

    // 用 ProcessFunction的侧输出流实现分流操作
    val highTempStream: DataStream[SensorReading] = dataStream
      .process( new SplitTempProcessor(30.0) )

    val lowTempStream = highTempStream.getSideOutput( new OutputTag[(String, Double, Long)]("low-temp") )

    // 打印输出
    highTempStream.print("high")
    lowTempStream.print("low")

    env.execute("side output job")
  }
}

// 自定义 ProcessFunction，用于区分高低温度的数据
class SplitTempProcessor(threshold: Double) extends ProcessFunction[SensorReading, SensorReading]{
  override def processElement(value: SensorReading, ctx: ProcessFunction[SensorReading, SensorReading]#Context, out: Collector[SensorReading]): Unit = {
    // 判断当前数据的温度值，如果大于阈值，输出到主流；如果小于阈值，输出到侧输出流
    if( value.temperature > threshold ){
      out.collect(value)
    } else {
      ctx.output( new OutputTag[(String, Double, Long)]("low-temp"), (value.id, value.temperature, value.timestamp) )
    }
  }
}