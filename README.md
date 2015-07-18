# sceval

ScEval, pronounced skev-al, is a highly Scalable evaluation framework using Spark for Scala

The intention is to PR this code it's way into MLLib, but it differs significantly from the MLLib API so I've dumped it here for people to review and comment.  I'm looking to get feedback so that when I do PR MLLib it will be accepted and won't be in vain.

## Dependencies

Has the following dependencies:

```
"org.apache.spark" % "spark-sql_2.10" % "1.3.0-cdh5.4.2",
"org.apache.spark" % "spark-core_2.10" % "1.3.0-cdh5.4.2",
"org.apache.spark" % "spark-mllib_2.10" % "1.3.0-cdh5.4.2"
```

If we added scalaz too then the code could be made more readible and terse, but for now just keeping dependencies to a minimum.

The unit tests are written in specs2.

## Contributing

If you wish to contribute please follow https://github.com/samthebest/dump/blob/master/convention-guidlines.md
