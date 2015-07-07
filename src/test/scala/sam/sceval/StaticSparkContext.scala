package sam.sceval

import org.apache.spark.{SparkConf, SparkContext}

object StaticSparkContext {
  val staticSc = new SparkContext(new SparkConf().setMaster("local").setAppName("Test Spark Engine"))
}
