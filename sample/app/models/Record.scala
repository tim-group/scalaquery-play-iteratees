package models

import play.api.db.DB
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, JsValue, Writes}
import play.api.libs.json.Json._
import play.api.Logger
import play.api.Play.current

import scala.slick.driver.H2Driver
import scala.slick.driver.H2Driver.simple._
import scala.slick.session.Database
import scala.slick.session.Database.threadLocalSession

import com.timgroup.scalaquery_play_iteratees.ScalaQueryPlayIteratees.{enumerateScalaQuery, PlayLogCallback}

case class Record(id: Int, name: String)

class Records extends Table[Record]("records") {
  def database = Database.forDataSource(DB.getDataSource("default"))
  lazy val profile = H2Driver

  // mapped columns
  def id   = column[Int   ]("id")
  def name = column[String]("name")
  def * = id ~ name <> (Record, Record.unapply _)

  def mkQuery = for { r <- this } yield r

  def count = database withSession { Query(mkQuery.length).first }

  def all = database withSession { mkQuery.list }

  /** This is it: enumerate the query for all Records in chunks of 2 */
  def enumerateAllInChunksOfTwo = enumerateScalaQuery(profile, Right(database), mkQuery, maybeChunkSize = Some(2),
    logCallback = PlayLogCallback(Logger /*, shouldLogSqlOnSuccess = true */ )) // uncomment to log SQL on successful fetches

  def ensureDbPopulated() {
    if (count == 0) {
      val records = Seq(
        Record(1, "Alpha"),
        Record(2, "Beta"),
        Record(3, "Gamma"),
        Record(4, "Delta"),
        Record(5, "Epsilon"))
      database withSession { this.insertAll(records:_*) }
    }
  }

  // serialize Record to json
  implicit object RecordWrites extends Writes[Record] {
    def writes(r: Record): JsValue = JsObject(Seq("id" -> toJson(r.id), "name" -> toJson(r.name)))
  }

}

object Records extends Records