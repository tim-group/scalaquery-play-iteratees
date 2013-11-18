package com.timgroup.scalaquery_play_iteratees

import scala.util.control.Exception.allCatch

import org.joda.time.DateTime

import org.scalaquery.ql.extended.ExtendedProfile
import org.scalaquery.ql.Query
import org.scalaquery.session.SessionWithAsyncTransaction
import org.scalaquery.session.Database

import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumerator


object ScalaQueryPlayIteratees {

  /** Default chunk size for queries */
  val DefaultQueryChunkSize: Int = 100

  /** Fields passed to callback after each query execution */
  case class LogFields(startTime: DateTime,
                       endTime: DateTime,
                       maybeSqlStmt: Option[String],      // sql select statement unless generation throws exception
                       maybeException: Option[Throwable]) // [failure cases only] thrown exception

  /** A LogCallback is defined as an effect taking LogFields as input */
  type LogCallback = LogFields => Unit

  /** Default LogCallback is to do nothing */
  val DefaultLogCallback: LogCallback = LogFields => ()

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
                                   logCallback: LogCallback = DefaultLogCallback): Enumerator[List[R]] = {
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
