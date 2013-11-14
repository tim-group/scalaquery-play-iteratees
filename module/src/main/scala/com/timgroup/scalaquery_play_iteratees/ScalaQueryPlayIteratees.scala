package com.timgroup.scalaquery_play_iteratees

import scala.util.control.Exception.allCatch

import org.joda.time.DateTime

import org.scalaquery.ql.extended.ExtendedProfile
import org.scalaquery.ql.Query
import org.scalaquery.session.SessionWithAsyncTransaction
import org.scalaquery.session.Database

import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumerator

// TODO (2013-10-25, msiegel/broberts): In order of current priority
//
//   1. [x] Need an error handling story here, which is test-driven
//      * Propagate Thrown side of Promises
//      * Short circuit when encounter a Thrown
//      * Ultimate iteratee[C].flatMap(_.run) = Thrown(ex) in that case
//
//   2. [x] Need consistent reads across chunks on MySQL
//      * Take implicit or explicit session (alternate api still creates if not given and starts transaction)
//      * Document that session must be in a transaction for consistent reads
//      * With InnoDB, this should just work: http://dev.mysql.com/doc/refman/5.1/en/innodb-consistent-read.html
//      * Test drive with an Enumeratee that pauses after each chunk, and test inserting, updating,
//        and deleting rows while pipeline is paused (wire up a new MySQL data source in application.conf
//        while inside Ideas FX, and after extracting, use the TestDB infrastructure for these tests)
//
//   3. [x] Need a structured logging as part of the error handling story, but
//      in a way that can be factored out of this library into optional callbacks
//        * [x] log, for each successful chunk fetched,
//                -start time, end time, duration,
//                -optional request ID
//        * [x] log, for each failure
//                -same fields as above
//                -allCatch opt { query.selectStatement }.getOrElse("")
//                -throwable
//
//   4. [ ] Need to extract this into a free-standing library like scalaquery_nested
//
object ScalaQueryPlayIteratees {

  /** Default chunk size for queries */
  val DefaultQueryChunkSize: Int = 100

  /** Fields passed to callback after each query execution */
  case class LogFields(
                        startTime: DateTime,
                        endTime: DateTime,
                        // provide sql select statement, unless generating it throws an exception
                        maybeSqlStmt: Option[String],
                        // [failure only] provide any exception thrown
                        maybeException: Option[Throwable])

  type LogCallback = LogFields => Unit

  /**
   * Returns a Play Enumerator which fetches the results of the given ScalaQuery Query in chunks.
   *
   * @param sessionOrDatabase   Provide either a session (useful for consistent reads across a
   *                            larger transaction), or a database with which to create a session.
   *                            NOTE: closes the transaction on the session regardless of whether
   *                              it was passed in or created from a database.
   */
  def enumerateScalaQuery[Q, E, R](driverProfile: ExtendedProfile,
                                   sessionOrDatabase: Either[SessionWithAsyncTransaction, Database],
                                   query: Query[Q, R],
                                   maybeChunkSize: Option[Int] = Some(DefaultQueryChunkSize),
                                   logCallback: LogCallback = LogFields => ()): Enumerator[List[R]] = {
    maybeChunkSize.filter(_ <= 0).foreach { _ => throw new IllegalArgumentException("chunkSize must be >= 1") }

    val session = sessionOrDatabase.fold(session => session, db => new SessionWithAsyncTransaction(db))
    val chunkedFetcher = new ChunkedScalaQueryFetcher(driverProfile, session, query, maybeChunkSize, logCallback)

    IterateesWithFixedExceptionHandling.Enumerator.fromCallback(
      () => chunkedFetcher.fetchNextChunk,
      () => chunkedFetcher.completeTransaction,
      (_, _) => chunkedFetcher.completeTransaction)
  }

  /**
   * Represents a stateful data pump to execute the query in chunks, for use in
   * constructing an Enumerator to represent the response chunks as a stream to
   * be fed to an Iteratee.
   *
   * NOTE: relies on the provided SessionWithAsyncTransaction, as well as the
   *   configuration of the underlying database, to ensure that read consistency
   *   is maintained across the fetching of multiple chunks.
   */
  private class ChunkedScalaQueryFetcher[Q, R](val driverProfile: ExtendedProfile,
                                               val session: SessionWithAsyncTransaction,
                                               val query: Query[Q, R],
                                               val maybeChunkSize: Option[Int],
                                               val logCallback: LogCallback) {
    import driverProfile.Implicit._

    /**
     * Mutable state for this enumeration of query results. Follows the pattern
     * of Enumerator.fromStream, which has an InputStream as mutable state.
     */
    private var position: Int = 0

    /** Returns a Promise containing None when no more results are available */
    def fetchNextChunk: Promise[Option[List[R]]] = {
      val startTime = DateTime.now

      // the only places errors might occur: sql generation, and fetching from db
      val queryAndSqlOrError = chunkQueryAndGenerateSql
      val promiseOrError = queryAndSqlOrError.right.flatMap { queryAndSql => executeQuery(queryAndSql._1) }

      // log what happened
      val endTime = DateTime.now
      val maybeSqlStatement = queryAndSqlOrError.right.toOption.flatMap(_._2)
      val maybeException = promiseOrError.left.toOption
      logCallback(LogFields(startTime, endTime, maybeSqlStatement, maybeException))

      // rethrow any exception, or return results
      promiseOrError.fold(e => throw e, p => p)
    }

    /** First place that an exception might occur: chunking the query, and generating the sql for logging */
    private def chunkQueryAndGenerateSql: Either[Throwable, (Option[Query[Q, R]], Option[String])] = allCatch either {
      val maybeQueryWithChunking = (maybeChunkSize, position) match {
        case (Some(chunkSize), _) => Some(query.drop(position).take(chunkSize))
        case (None, 0)            => Some(query)
        case _                    => None
      }

      val maybeSqlStatement = maybeQueryWithChunking.map(_.selectStatement)

      (maybeQueryWithChunking, maybeSqlStatement)
    }

    /** Second place that an exception might occur: executing the query */
    private def executeQuery(maybeQueryWithChunking: Option[Query[Q, R]]): Either[Throwable, Promise[Option[List[R]]]] = allCatch either {
      val results: List[R] = session.withAsyncTransaction { implicit sessionWithTransaction =>
        maybeQueryWithChunking match {
          case Some(query) => query.list
          case None        => Nil
        }
      }

      position += results.size // update mutable counter based on count of results fetched

      Promise.pure(Some(results).filterNot(_.isEmpty)) // return Promise(None) if no results
    }

    /**
     * When done, must call this to ensure that connection is committed, releasing any
     * underlying read locks or other mechanisms used by the database to ensure read
     * consistency across multiple statements in a single transaction.
     */
    def completeTransaction() {
      session.ensureAsyncTransactionIsCompleted()
    }
  }

}
