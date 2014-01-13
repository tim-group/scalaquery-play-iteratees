ScalaQueryPlayIteratees
=======================
[![Build Status](https://travis-ci.org/youdevise/scalaquery-play-iteratees.png)](https://travis-ci.org/youdevise/scalaquery-play-iteratees)

**NOTE**: This is branch **1.0.x**, for use with _Play 2.2.x_ and _Slick 1.0.x_ on Scala language _2.10.x_.

Go to the [master](https://github.com/youdevise/scalaquery-play-iteratees/tree/master) branch for use with Play 2.2.x and _Slick 2.0.x_ on Scala language 2.10.x.

Go to the [0.9.x](https://github.com/youdevise/scalaquery-play-iteratees/tree/0.9.x) branch for use with _Play 2.0.x_ and _ScalaQuery 0.10.0-M1_ on Scala language _2.9.x_.

News
----

There is work going on now to merge this library into the [play-slick](https://github.com/freekh/play-slick), see [freekh/play-slick#115](https://github.com/freekh/play-slick/issues/115). Please feel free to comment on that issue if you have suggestions or would like to help.


Summary
-------
Easily create a Play 2.2.x [Enumerator](http://www.playframework.com/documentation/2.2.x/Enumerators)
producing chunked results from any [Slick 1.0.x](https://github.com/slick/slick/tree/1.0) query!
 *  Stream chunked results from any `Query` as an `Enumerator`! 
 *  Easily plug into Play 2.2.x streaming, such as
    [Comet](http://www.playframework.com/documentation/2.2.x/ScalaComet)
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
  val pipeline = Records.enumerateAllInChunksOfTwo &>
    Enumeratee.map(toJson(_)) &>
    Comet(callback = "parent.cometMessage")

  Ok.chunked(pipeline)
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
---------------------

##### Scala 2.10.x + Play 2.2.x + Slick 1.0.1: #####

The latest published release is **[1.0.0-M1](http://oss.sonatype.org/content/repositories/releases/com/timgroup/scalaquery-play-iteratees_2.10/1.0.0-M1/)**. Add dependency to **project/Build.scala** as: `"com.timgroup" %% "scalaquery-play-iteratees" % "1.0.0-M1"`


##### Scala 2.9.x + Play 2.0.x + ScalaQuery 0.10.0-M1: #####

Please see branch [0.9.x](https://github.com/youdevise/scalaquery-play-iteratees/tree/0.9.x).


Status
------

This code is working in production code. Questions and pull requests
are welcome. No support is currently planned for Play 2.1.x at this
time.


Copyright
---------

See [MIT-LICENSE.txt](MIT-LICENSE.txt) for details.
