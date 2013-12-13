package com.timgroup.scalaquery_play_iteratees

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito._
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.path
import org.slf4j.{ Logger => Slf4jLogger }
import play.api.Logger

import ScalaQueryPlayIteratees.{LogFields, PlayLogCallback}

class PlayLogCallbackUnitSpec extends path.FunSpec with MustMatchers with MockitoSugar {
  val startTime = new DateTime(2013, 11, 19, 17, 8, 3, DateTimeZone.forID("America/New_York"))
  val endTime = startTime.plusMillis(500)
  val fieldsOnSuccess = LogFields(startTime, endTime, Some("sql"), None)
  val exception = new RuntimeException("boo!")
  val fieldsOnException = LogFields(startTime, endTime, Some("sql"), Some(exception))

  val slf4jLogger = mock[Slf4jLogger]
  when(slf4jLogger.isInfoEnabled).thenReturn(true)
  when(slf4jLogger.isErrorEnabled).thenReturn(true)

  val playLogger = new Logger(slf4jLogger)
  val callback = PlayLogCallback(playLogger)
  val callbackLogSql = PlayLogCallback(playLogger, shouldLogSqlOnSuccess = true)

  describe("PlayLogCallback") {

    describe("on success with default settings") {
      callback(fieldsOnSuccess)

      it("logs at info level without SQL") {
        verify(slf4jLogger).info("enumerateScalaQuery - fetched chunk in 500 ms")
      }
    }

    describe("on success with settings: should log SQL on success") {
      callbackLogSql(fieldsOnSuccess)

      it("logs at info level *with* SQL") {
        verify(slf4jLogger).info("enumerateScalaQuery - fetched chunk in 500 ms [sql]")
      }
    }

    describe("on failure with default settings") {
      callback(fieldsOnException)

      it("logs at error level with SQL") {
        verify(slf4jLogger).error("enumerateScalaQuery - failed to fetch chunk in 500 ms [sql]", exception)
      }
    }

    describe("on failure with settings: should log SQL on success") {
      callbackLogSql(fieldsOnException)

      it("logs at error level with SQL") {
        verify(slf4jLogger).error("enumerateScalaQuery - failed to fetch chunk in 500 ms [sql]", exception)
      }
    }

  }

}
