package org.scalaquery.session


/**
 * Reimplementation of the transaction semantics of ScalaQuery's BaseSession#withTransaction,
 * but in an API which can be driven from multiple asynchronous callbacks rather than only a
 * single block.
 *
 * NOTE: Used by ScalaQueryPlayIteratees to provide read consistency across chunked reads.
 */
class SessionWithAsyncTransaction(db: Database) extends BaseSession(db) {
  var hasTransactionFailed = false // don't allow asynchronously re-opening after a failed transaction

  /**
   * Execute the given block with an active async transaction
   *
   * NOTE: If the given block throws an exception, then:
   *   1. The open transaction will be rolled back
   *   2. Further calls are not allowed, throwing IllegalStateException, create a new Session instead
   */
  def withAsyncTransaction[T](f: Session => T): T = {
    ensureAsyncTransactionIsStarted()

    var doneWithThisBlock = false
    try {
      val result = f(this)
      doneWithThisBlock = true
      result
    } finally {
      if (!doneWithThisBlock) {
        doRollback = true
        hasTransactionFailed = true
        ensureAsyncTransactionIsCompleted()
      }
    }
  }

  /** Bring any active transaction to completion, resulting in either commit or rollback */
  def ensureAsyncTransactionIsCompleted() {
    if (inTransaction) { completeAsyncTransaction() }
  }

  def ensureAsyncTransactionIsStarted() {
    if (!inTransaction) { startAsyncTransaction() }
  }

  def startAsyncTransaction() {
    if (hasTransactionFailed) { throw new IllegalStateException("An async transaction was already rolled back on this session. A new session is required to create a new transaction") }
    if (inTransaction) { throw new IllegalStateException("An async transaction was already in progress") }

    conn.setAutoCommit(false)
    inTransaction = true
    doRollback = false
  }

  def completeAsyncTransaction() {
    if (!inTransaction) { throw new IllegalStateException("No async transaction was in progress") }

    try {
      if (doRollback) { conn.rollback() } else { conn.commit() }
    } finally {
      conn.setAutoCommit(true)
      inTransaction = false
    }
  }

}