package com.timgroup.scalaquery_play_iteratees

import java.util.concurrent.TimeoutException
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.MustMatchers
import org.scalatest.path

import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

class IterateesWithFixedExceptionHandlingFunctionalSpec extends path.FunSpec with MustMatchers {
  val parseableCallbackPromise: Future[Option[String]] = Future.successful(Some("42"))
  val unparseableCallbackPromise: Future[Option[String]] = Future.successful(Some("not an Int"))
  val failedCallbackPromise: Future[Option[String]] = Future.failed(new RuntimeException("boo!"))

  describe("Enumerator.fromCallback") {

    describe("[Demonstrate problem] Pipeline of Enumerator.fromCallback &> Enumeratee |>> Iteratee") {

      it("fails to return a failed promise containing an exception thrown in Enumerator") {
        val enumerator = Enumerator.generateM(failedCallbackPromise)
        val enumeratee = Enumeratee.map { (s: String) => s.toInt }
        val iteratee = Iteratee.fold[Int, Int](0) { (sum, n) => sum + n }
        val eventuallyResult = (enumerator &> enumeratee |>> iteratee).flatMap(_.run)

        // NOTE (2013-10-25, msiegel/broberts): Instead of propagating exception, it times out
        //val thrown = evaluating { eventuallyResult.await(100).get } must produce [RuntimeException]
        //thrown.getMessage must equal("boo!")

        // Demonstrate that the following problem exists to motivate our fix:
        //evaluating { Await.result(eventuallyResult, 100 millis) } must produce [TimeoutException]
        Await.result(eventuallyResult, 100 millis)
      }

    }

    describe("[Demonstrate problem] Enumerator.fromCallback") {

      it("fails to pass along a failed promise containing an exception thrown in callback") {
        val enumerator = Enumerator.generateM(failedCallbackPromise)
        val iteratee = Iteratee.ignore[String]
        val eventuallyResult = (enumerator |>> iteratee).flatMap(_.run)

        // NOTE (2013-10-25, msiegel/broberts): Instead of propagating exception, it times out
        //val thrown = evaluating { eventuallyResult.await(100).get } must produce [RuntimeException]
        //thrown.getMessage must equal("boo!")

        // Demonstrate that the following problem exists to motivate our fix:
        //evaluating { Await.result(eventuallyResult, 100 millis) } must produce [TimeoutException]
        Await.result(eventuallyResult, 100 millis)
      }

    }

//    describe("IterateesWithFixedExceptionHandling.Enumerator.fromCallback") {
//
//      it("passes along a failed promise containing an exception thrown in callback") {
//        val enumerator = IterateesWithFixedExceptionHandling.Enumerator.fromCallback(() => failedCallbackPromise)
//        val iteratee = Iteratee.ignore[String]
//        val eventuallyResult = (enumerator |>> iteratee).flatMap(_.run)
//
//        val thrown = evaluating { eventuallyResult.await(100).get } must produce [RuntimeException]
//        thrown.getMessage must equal("boo!")
//      }
//
//    }
//
//    describe("Pipeline of fixed Enumerator.fromCallback &> Enumeratee |>> Iteratee") {
//
//      it("returns a failed promise containing an exception thrown in Enumerator") {
//        val enumerator = IterateesWithFixedExceptionHandling.Enumerator.fromCallback(() => failedCallbackPromise)
//        val enumeratee = Enumeratee.map { (s: String) => s.toInt }
//        val iteratee = Iteratee.fold[Int, Int](0) { (sum, n) => sum + n }
//        val eventuallyResult = (enumerator &> enumeratee |>> iteratee).flatMap(_.run)
//
//        val thrown = evaluating { eventuallyResult.await(100).get } must produce [RuntimeException]
//        thrown.getMessage must equal("boo!")
//      }
//
//    }

  }

}
