package com.timgroup.scalaquery_play_iteratees

import scala.language.postfixOps
import scala.util.{Try, Random}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.slick.testkit.util.TestDB
import org.h2.jdbc.JdbcSQLException
import org.scalatest.exceptions.TestFailedException
import org.scalatest.OptionValues._
import org.scalatest.Matchers
import org.scalatest.path
import play.api.libs.iteratee.{Enumeratee, Error, Input, Iteratee}
import scala.slick.driver.{QueryBuilderInput, H2Driver, ExtendedProfile}
import scala.slick.session.Database.threadLocalSession
import scala.slick.session.SessionWithAsyncTransaction

import ScalaQueryPlayIteratees.{LogFields, LogCallback, enumerateScalaQuery}

class ScalaQueryPlayIterateesFunctionalSpec extends path.FunSpec with Matchers {
  // Create in-memory test DB and import its implicits
  val tdb = new TestDB("h2mem", scala.slick.driver.H2Driver) {
    val url = "jdbc:h2:mem:scalaquery-play-iteratees_spec;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_MODE=1"
    val jdbcDriver = "org.h2.Driver"
  }

  lazy val db = tdb.createDB()
  import tdb.driver.simple._

  // names for certain sets of test rows
  val fiveRowsInDb = List((1, "a"), (2, "b"), (3, "c"), (4, "d"), (5, "e")).map(TestRow.tupled)
  val twoRowsInDb = fiveRowsInDb.take(2)
  val rowsInDbExcludingC = fiveRowsInDb.filterNot(_.name == "c")

  describe(".enumerateScalaQuery") {

    describe("Basic happy path") {

      it("should enumerate query results in 0 chunks when chunkSize = 2 and query has 0 results") {
        testChunkedEnumerationUsingInMemoryDb(Nil, Some(2), Nil)
      }

      it("should enumerate query results in 1 chunk when chunkSize = 2 and query has 2 results") {
        testChunkedEnumerationUsingInMemoryDb(twoRowsInDb, Some(2), List(twoRowsInDb))
      }

      it("should enumerate query results in 3 chunks when chunkSize = 2 and query has 5 results") {
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), fiveRowsInDb.grouped(2).toList)
      }

      it("should enumerate query results in 2 chunks when chunkSize = 2 and query has 4 results after applying criteria") {
        val criterion: TestQueryCriterion = _.name isNot "c"
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), rowsInDbExcludingC.grouped(2).toList, Some(rowsInDbExcludingC), Seq(criterion))
      }

      it("should enumerate query results in 1 chunk when chunkSize = 10 and query has 5 results") {
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(10), List(fiveRowsInDb))
      }

      it("should enumerate query results in 1 chunk when no chunkSize and query has 5 results") {
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, None, List(fiveRowsInDb))
      }

      it("should close transaction after successful execution") {
        val session = new SessionWithAsyncTransaction(db)
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, None, List(fiveRowsInDb), maybeExternalSession = Some(session))
        session.inTransaction should be(false)
      }

      it("should close underlying jdbc connection after successful execution") {
        val session = new SessionWithAsyncTransaction(db)
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, None, List(fiveRowsInDb), maybeExternalSession = Some(session))
        session.open should be(false)
      }

    }

    describe("Error handling") {

      it("should throw argument exception if chunkSize <= 0") {
        evaluating { testChunkedEnumerationUsingInMemoryDb(Nil, Some(0), Nil) } should produce [IllegalArgumentException]
      }

      it("should propagate exception generated during query execution") {
        val criterion: TestQueryCriterion = (_.doesNotExist isNotNull)
        val thrown = evaluating { testChunkedEnumerationUsingInMemoryDb(Nil, None, Nil, criteria = Seq(criterion)) } should produce [JdbcSQLException]
        thrown.getMessage should startWith ("""Column "x2.DOES_NOT_EXIST" not found;""")
      }

      it("should close transaction when exception generated during query execution") {
        val session = new SessionWithAsyncTransaction(db)
        val criterion: TestQueryCriterion = (_.doesNotExist isNotNull)
        evaluating { testChunkedEnumerationUsingInMemoryDb(Nil, None, Nil, criteria = Seq(criterion), maybeExternalSession = Some(session)) } should produce [JdbcSQLException]
        session.inTransaction should be(false)
      }

      it("should close underlying jdbc connection when exception generated during query execution") {
        val session = new SessionWithAsyncTransaction(db)
        val criterion: TestQueryCriterion = (_.doesNotExist isNotNull)
        evaluating { testChunkedEnumerationUsingInMemoryDb(Nil, None, Nil, criteria = Seq(criterion), maybeExternalSession = Some(session)) } should produce [JdbcSQLException]
        session.open should be(false)
      }

      it("should close transaction when exception generated in downstream Enumeratee") {
        val session = new SessionWithAsyncTransaction(db)
        val exceptionThrowingEnumeratee = Enumeratee.map { chunk: List[TestRow] => throw new RuntimeException("boo!"); chunk }
        evaluating {
          testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), rowsInDbExcludingC.grouped(2).toList,
            maybeExtraEnumeratee = Some(exceptionThrowingEnumeratee),
            maybeExternalSession = Some(session))
        } should produce [RuntimeException]
        session.inTransaction should be(false)
      }

      it("should close underlying jdbc connection when exception generated in downstream Enumeratee") {
        val session = new SessionWithAsyncTransaction(db)
        val exceptionThrowingEnumeratee = Enumeratee.map { chunk: List[TestRow] => throw new RuntimeException("boo!"); chunk }
        evaluating {
          testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), rowsInDbExcludingC.grouped(2).toList,
            maybeExtraEnumeratee = Some(exceptionThrowingEnumeratee),
            maybeExternalSession = Some(session))
        } should produce [RuntimeException]
        session.open should be(false)
      }

      it("should close transaction when downstream Enumeratee is in Error state") {
        val session = new SessionWithAsyncTransaction(db)
        val errorStateEnumeratee = new Enumeratee[List[TestRow], List[TestRow]] {
          def applyOn[A](inner: Iteratee[List[TestRow], A]) = Error("testing!", Input.Empty)
        }
        evaluating {
          testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), rowsInDbExcludingC.grouped(2).toList,
            maybeExtraEnumeratee = Some(errorStateEnumeratee),
            maybeExternalSession = Some(session))
        } should produce [RuntimeException]
        session.inTransaction should be(false)
      }

      it("should close underlying jdbc connection when downstream Enumeratee is in Error state") {
        val session = new SessionWithAsyncTransaction(db)
        val errorStateEnumeratee = new Enumeratee[List[TestRow], List[TestRow]] {
          def applyOn[A](inner: Iteratee[List[TestRow], A]) = Error("testing!", Input.Empty)
        }
        evaluating {
          testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), rowsInDbExcludingC.grouped(2).toList,
            maybeExtraEnumeratee = Some(errorStateEnumeratee),
            maybeExternalSession = Some(session))
        } should produce [RuntimeException]
        session.open should be(false)
      }

    }

    describe("Read consistency (transactions)") {

      it("should provide consistent reads *by default* when writes are interleaved") {
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), fiveRowsInDb.grouped(2).toList,
          maybeExtraEnumeratee = Some(createInterleavedWritesEnumeratee),
          maybeExternalSession = None)
      }

      it("should provide consistent reads given a real external session with async transaction") {
        testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), fiveRowsInDb.grouped(2).toList,
          maybeExtraEnumeratee = Some(createInterleavedWritesEnumeratee),
          maybeExternalSession = Some(new SessionWithAsyncTransaction(db)))
      }

      it("[test of preceding test] should fail to provide consistent reads given a *fake* external session with async transaction") {
        val thrown = evaluating {
          testChunkedEnumerationUsingInMemoryDb(fiveRowsInDb, Some(2), fiveRowsInDb.grouped(2).toList,
            maybeExtraEnumeratee = Some(createInterleavedWritesEnumeratee),
            maybeExternalSession = Some(new FakeSessionWithAsyncTransactionForTesting(db)))
        } should produce [TestFailedException]
        thrown.getMessage should endWith ("was not equal to " + fiveRowsInDb.toString)
      }

    }

    describe("Logging callbacks") {

      describe("when fetch is successful") {
        it("should log startTime and endTime") { testLoggedStartAndEndTimes(scenario = Success) }
        it("should log sql statement")         { testLoggedSqlStatement(scenario = Success) }
        it("should *not* log an exception")    { testLoggedNoException(scenario = Success) }
      }

      describe("when fetch throws an exception") {
        it("should log startTime and endTime") { testLoggedStartAndEndTimes(scenario = ThrowInFetch) }
        it("should log sql statement")         { testLoggedSqlStatement(scenario = ThrowInFetch) }
        it("should log the exception")         { testLoggedException(scenario = ThrowInFetch) }
      }

      describe("when sql generation throws an exception") {
        it("should log startTime and endTime") { testLoggedStartAndEndTimes(scenario = ThrowInSqlGen) }
        it("should *not* log sql statement")   { testLoggedNoSqlStatement(scenario = ThrowInSqlGen) }
        it("should log the exception")         { testLoggedException(scenario = ThrowInSqlGen, exceptionClass = classOf[TestSqlGenException]) }
      }

    }

  }

  case class TestRow(id: Int, name: String)

  // Schema contains fewer columns than TestTable definition below, to allow testing of queries for non-existent columns
  class TestTableSchema extends Table[TestRow]("TEST") {
    def id = column[Int]("ID")
    def name = column[String]("NAME")
    def * = id ~ name <> (TestRow, TestRow.unapply _)
  }

  class TestTable extends Table[TestRow]("TEST") {
    def id = column[Int]("ID")
    def name = column[String]("NAME")
    def doesNotExist = column[String]("DOES_NOT_EXIST") // this NamedColumn will not be in SQL schema above
    def * = id ~ name <> (TestRow, TestRow.unapply _)
  }

  type TestQueryCriterion = (TestTable) => Column[Boolean]

  def testChunkedEnumerationUsingInMemoryDb(rowsInDb: List[TestRow],
                                            maybeChunkSize: Option[Int],
                                            expectedChunksSent: List[List[TestRow]],
                                            maybeExpectedResults: Option[List[TestRow]] = None,
                                            criteria: Seq[TestQueryCriterion] = Nil,
                                            maybeExtraEnumeratee: Option[Enumeratee[List[TestRow], List[TestRow]]] = None,
                                            maybeExternalSession: Option[SessionWithAsyncTransaction] = None,
                                            maybeExtendedProfile: Option[ExtendedProfile] = None,
                                            logCallback: LogCallback = _ => ()) {
    // Create table if not exist, and insert rows
    val testTable = new TestTable
    db withSession {
      val schema = new TestTableSchema
      try { schema.ddl.drop }
      catch { case ex: JdbcSQLException if ex.getMessage.contains("Table \"TEST\" not found") => /* ignore */ }
      schema.ddl.create
      schema.insertAll(rowsInDb:_*)
    }

    // Query the table
    val baseQuery = for { test <- testTable } yield test
    baseQuery.selectStatement
    def mkQuery(criteria: TestQueryCriterion*) = criteria.foldLeft(baseQuery) { (q, c) => q.filter(c) }

    // Enumerate in chunks, through an Enumeratee which records each chunk as an effect, and an
    //   optional extra Enumeratee passed in by the caller, into a consuming Iteratee
    val sessionOrDatabase = maybeExternalSession.toLeft(db)
    val driver = maybeExtendedProfile.getOrElse(tdb.driver)
    val enumerator = enumerateScalaQuery(driver, sessionOrDatabase, mkQuery(criteria:_*), maybeChunkSize, logCallback)

    var chunksSent: List[List[TestRow]] = Nil
    val chunksSentEnumeratee = Enumeratee.map { chunk: List[TestRow] => chunksSent = chunksSent :+ chunk; chunk }
    val identityEnumeratee = Enumeratee.map { chunk: List[TestRow] => chunk }
    val extraEnumeratee = maybeExtraEnumeratee.getOrElse(identityEnumeratee)

    val iteratee = Iteratee.consume[List[TestRow]]()

    // Run the Enumerator -> Enumeratee(s) -> Iteratee pipeline
    val eventuallyResult = (enumerator &> chunksSentEnumeratee &> extraEnumeratee |>> iteratee).flatMap(_.run)
    val result = Await.result(eventuallyResult, 5 seconds)

    // Verify expectations
    result should be(maybeExpectedResults.getOrElse(rowsInDb)) // returned expected rows
    chunksSent should be(expectedChunksSent)                   // chunked as expected
  }

  def createInterleavedWritesEnumeratee: Enumeratee[List[TestRow], List[TestRow]] = {
    // mutable state which will be closed over by the function
    var numRowsLeftToAdd = 2

    // asynchronous effect: adds a random row, and mutates above counter
    // NOTE (2013-10-30, msiegel): made this async to handle databases (such as H2),
    //   whose "consistent reads" mode causes the writes to be blocked by locks until
    //   the read completes
    def addNewRowAsync() {
      if (numRowsLeftToAdd > 0) {
        Future { addNewRow() }
        Thread.sleep(500) // give it a chance to try (makes the test fail before the fix)
        numRowsLeftToAdd -= 1
      }
    }

    def addNewRow() {
      db withSession { new TestTableSchema().insert(TestRow(Random.nextInt(), Random.alphanumeric.take(5).mkString)) }
    }

    // return an enumeratee which calls the above function on each chunk
    Enumeratee.map { chunk: List[TestRow] => addNewRowAsync(); chunk }
  }

  /** Fulfills this interface, but uses only local sessions for each block */
  class FakeSessionWithAsyncTransactionForTesting(db: Database) extends SessionWithAsyncTransaction(db) {
    override def withAsyncTransaction[T](f: (Session) => T) = db.withSession(f)
  }

  sealed trait TestScenariosForLogging
  case object Success extends TestScenariosForLogging
  case object ThrowInFetch extends TestScenariosForLogging
  case object ThrowInSqlGen extends TestScenariosForLogging

  def testLoggedStartAndEndTimes(scenario: TestScenariosForLogging) {
    val logged = testLogging(scenario)
    logged.startTime.getMillis should be <= (logged.endTime.getMillis)
  }

  def testLoggedNoSqlStatement(scenario: TestScenariosForLogging) {
    testLogging(scenario).maybeSqlStmt should be(None)
  }

  def testLoggedSqlStatement(scenario: TestScenariosForLogging) {
    testLogging(scenario).maybeSqlStmt.value should include ("limit")
  }

  def testLoggedNoException(scenario: TestScenariosForLogging) {
    testLogging(scenario).maybeException should be(None)
  }

  def testLoggedException(scenario: TestScenariosForLogging, exceptionClass: Class[_] = classOf[JdbcSQLException]) {
    val maybeException = testLogging(scenario).maybeException
    maybeException should be('defined)
    maybeException.value.getClass should be(exceptionClass)
  }

  def testLogging(scenario: TestScenariosForLogging): LogFields = {
    val promisedLogged = Promise[LogFields]()

    Try {
      val throwingCriterion: TestQueryCriterion = (_.doesNotExist isNotNull)

      val criteria = scenario match {
        case ThrowInFetch => Seq(throwingCriterion)
        case _            => Nil
      }

      val maybeExtendedProfile = scenario match {
        case ThrowInSqlGen => Some(new H2Driver {
          override def createQueryBuilder(input: QueryBuilderInput): QueryBuilder = { throw new TestSqlGenException() }
        })
        case _ => None
      }

      testChunkedEnumerationUsingInMemoryDb(
        fiveRowsInDb, Some(2), fiveRowsInDb.grouped(2).toList,
        criteria = criteria,
        maybeExtendedProfile = maybeExtendedProfile,
        logCallback = f => { promisedLogged.trySuccess(f) })
    }

    Await.result(promisedLogged.future, 5 seconds)
  }

  class TestSqlGenException extends RuntimeException

}
