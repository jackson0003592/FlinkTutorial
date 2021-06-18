package day4

import com.atguigu.apitest.SensorReading
import org.apache.flink.api.java.tuple.{Tuple, Tuple1}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, TumblingProcessingTimeWindows, WindowAssigner}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/**
  * Copyright (c) 2018-2028 尚硅谷 All Rights Reserved
  *
  * Project: FlinkTutorial
  * Package: com.atguigu.apitest
  * Version: 1.0
  *
  * Created by wushengran on 2020/4/18 14:13
  */
object WindowTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.getConfig.setAutoWatermarkInterval(100L)

//    val inputStream: DataStream[String] = env.readTextFile("D:\\Projects\\BigData\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    val inputStream = env.socketTextStream("hadoop102", 7777)
    val dataStream: DataStream[SensorReading] = inputStream
      .map( data => {
        val dataArray = data.split(",")
        SensorReading( dataArray(0), dataArray(1).toLong, dataArray(2).toDouble )
      } )
//      .assignAscendingTimestamps(_.timestamp * 1000L)
//      .assignTimestampsAndWatermarks( new MyWMAssigner(1000L) )
      .assignTimestampsAndWatermarks( new BoundedOutOfOrdernessTimestampExtractor[SensorReading](Time.seconds(1)) {
      override def extractTimestamp(element: SensorReading): Long = element.timestamp * 1000L
    } ).setParallelism(2)

    val resultStream = dataStream
      .keyBy("id")
//      .window( EventTimeSessionWindows.withGap(Time.minutes(1)) )    // 会话窗口
//      .window( TumblingProcessingTimeWindows.of(Time.days(1), Time.hours(-8)) )
      .timeWindow(Time.seconds(15), Time.seconds(5))
        .allowedLateness(Time.minutes(1))
        .sideOutputLateData(new OutputTag[SensorReading]("late"))
//      .reduce( new MyReduce() )
      .apply( new MyWindowFun() )

    dataStream.print("data2")
    resultStream.getSideOutput(new OutputTag[SensorReading]("late"))
    resultStream.print("result2")
    env.execute("window test")
  }
}

// 自定义一个全窗口函数
class MyWindowFun() extends WindowFunction[SensorReading, (String, Long, Int), Tuple, TimeWindow]{
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[SensorReading], out: Collector[(String, Long, Int)]): Unit = {
    val id: String = key.asInstanceOf[Tuple1[String]].f0
    out.collect((id, window.getStart, input.size))
  }
}

// 自定义一个周期性生成watermark的Assigner
class MyWMAssigner(lateness: Long) extends AssignerWithPeriodicWatermarks[SensorReading]{
  // 需要两个关键参数，延迟时间，和当前所有数据中最大的时间戳
//  val lateness: Long = 1000L
  var maxTs: Long = Long.MinValue + lateness

  override def getCurrentWatermark: Watermark =
    new Watermark(maxTs - lateness)

  override def extractTimestamp(element: SensorReading, previousElementTimestamp: Long): Long = {
    maxTs = maxTs.max(element.timestamp * 1000L)
    element.timestamp * 1000L
  }
}

// 自定义一个断点式生成watermark的Assigner
class MyWMAssigner2 extends AssignerWithPunctuatedWatermarks[SensorReading]{
  val lateness: Long = 1000L
  override def checkAndGetNextWatermark(lastElement: SensorReading, extractedTimestamp: Long): Watermark = {
    if( lastElement.id == "sensor_1" ){
      new Watermark(extractedTimestamp - lateness)
    } else
      null
  }

  override def extractTimestamp(element: SensorReading, previousElementTimestamp: Long): Long =
    element.timestamp * 1000L
}