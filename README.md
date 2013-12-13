ScalaQueryPlayIteratees
=======================
[![Build Status](https://travis-ci.org/youdevise/scalaquery-play-iteratees.png)](https://travis-ci.org/youdevise/scalaquery-play-iteratees)

**NOTE**: This is branch **0.9.x**, for use with Play 2.0.x and ScalaQuery 0.10.0-M1 on Scala language 2.9.x.

Go to the [master](https://github.com/youdevise/scalaquery-play-iteratees/tree/master) branch for use with Play 2.2.x and Slick on Scala language 2.10.x.

Summary
-------
Easily create a Play 2.0.x [Enumerator](http://www.playframework.com/documentation/2.0.x/Enumerators)
producing chunked results from any [ScalaQuery 0.10.0-M1](https://github.com/slick/slick/tree/0.10.0-M1)
query!
 *  Stream chunked results from any `Query` as an `Enumerator`! 
 *  Easily plug into Play 2.0.x streaming, such as
    [Comet](http://www.playframework.com/documentation/2.0.x/ScalaComet)
 *  Underlying database transaction provides read consistency across async chunked reads
 *  Easy logging callbacks 
 *  Robust error handling
 *  Well-tested library extracted from production code

Usage
-----

```scala
//
// Controller
//
def listRecordsViaComet = Action { request =>
  val pipeline = (
    Records.enumerateAllInChunksOfTwo
      &> Enumeratee.map(toJson(_))
      &> Comet(callback = "parent.cometMessage"))

  Ok.stream(pipeline)
}

//
// Model
//
class Records extends Table[Record]("records") {
  def mkQuery = for { r <- this } yield r
  
  def enumerateAllInChunksOfTwo = 
    enumerateScalaQuery(profile, Right(database), mkQuery, maybeChunkSize = Some(2))
}
```

See the included [sample play app](sample) for a working example.

Adding SBT Dependency
------------------------------

##### Scala 2.9.x + Play 2.0.x + ScalaQuery 0.10.0-M1: #####

The latest published release is **[0.9.1](http://oss.sonatype.org/content/repositories/releases/com/timgroup/scalaquery-play-iteratees_2.9.1/0.9.1/)**. Add dependency to **project/Build.scala** as: `"com.timgroup" %% "scalaquery-play-iteratees" % "0.9.1"`

Status
------

This code is working in production code based on Play 2.0.8, ScalaQuery 0.10.0-M1, on Scala 2.9.2. 


Copyright
---------

See [MIT-LICENSE.txt](MIT-LICENSE.txt) for details.
