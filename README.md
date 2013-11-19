ScalaQueryPlayIteratees
=======================
[![Build Status](https://travis-ci.org/youdevise/scalaquery-play-iteratees.png)](https://travis-ci.org/youdevise/scalaquery-play-iteratees)

Summary
-------
Easily create a Play 2.0.x [Enumerator](http://www.playframework.com/documentation/2.0.x/Enumerators)
producing chunked results from any [ScalaQuery 0.10.0-M1](https://github.com/slick/slick/tree/0.10.0-M1)
query!
 *  Use any `Query` as an `Enumerator` which plugs right in to Play 2.0.x
    [Comet](http://www.playframework.com/documentation/2.0.x/ScalaComet) support or
    other asynchronous processing
 *  Underlying database transaction provides read consistency across async chunked reads
 *  Easy logging callbacks 
 *  Robust error handling
 *  Well-tested library extracted from production code
