package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

class IntegrationSpec extends Specification {

  "Application" should {

    "return all records, which were enumerated in chunks" in {
      running(TestServer(3333), HTMLUNIT) { browser =>

        // verify that ajax loaded correctly, which queried db in chunks
        browser.goTo("http://localhost:3333/list.json")
        browser.pageSource must contain("""{"id":1,"name":"Alpha"}""")
        browser.pageSource must contain("""{"id":2,"name":"Beta"}""")
        browser.pageSource must contain("""{"id":3,"name":"Gamma"}""")
        browser.pageSource must contain("""{"id":4,"name":"Delta"}""")
        browser.pageSource must contain("""{"id":5,"name":"Epsilon"}""")
      }
    }

  }

}