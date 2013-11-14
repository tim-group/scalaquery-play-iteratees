package com.timgroup.scalaquery_play_iteratees

import org.scalatest.matchers.MustMatchers
import org.scalatest.path

class ScalaQueryPlayIterateesFunctionalSpec extends path.FunSpec with MustMatchers {

  describe(".enumerateScalaQuery") {

    describe("Basic happy path") {

      it("should enumerate query results in 0 chunks when chunkSize = 2 and query has 0 results") {
        pendingUntilFixed { true must be(false) }
      }

    }

  }

}