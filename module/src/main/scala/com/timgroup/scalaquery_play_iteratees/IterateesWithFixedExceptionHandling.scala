//package com.timgroup.scalaquery_play_iteratees
//
//import play.api.libs.concurrent.{Thrown, Redeemed, Promise}
//import play.api.libs.iteratee.{Iteratee, Enumerator, Input}
//
//object IterateesWithFixedExceptionHandling {
//
//  object Enumerator {
//
//    /** Fixed version has only one additional clause from the 2.0.x version: the Thrown(e) case at the bottom */
//    def fromCallback[E](retriever: () => Promise[Option[E]],
//                        onComplete: () => Unit = () => (),
//                        onError: (String, Input[E]) => Unit = (_: String, _: Input[E]) => ()) = new Enumerator[E] {
//      def apply[A](it: Iteratee[E, A]): Promise[Iteratee[E, A]] = {
//
//        val iterateeP = Promise[Iteratee[E, A]]()
//
//        def step(it: Iteratee[E, A]) {
//
//          val next = it.fold(
//            (a, e) => { iterateeP.redeem(it); Promise.pure(None) },
//            k => {
//              retriever().map {
//                case None => {
//                  val remainingIteratee = k(Input.EOF)
//                  iterateeP.redeem(remainingIteratee)
//                  None
//                }
//                case Some(read) => {
//                  val nextIteratee = k(Input.El(read))
//                  Some(nextIteratee)
//                }
//              }
//            },
//            (_, _) => { iterateeP.redeem(it); Promise.pure(None) }
//          )
//
//          next.extend1 {
//            case Redeemed(Some(i)) => step(i)
//            // NOTE (2013-10-25, broberts/msiegel): Adding following lines fixes exception handling
//            //   see also https://github.com/playframework/playframework/blob/2.2.0/framework/src/iteratees/src/main/scala/play/api/libs/iteratee/Enumerator.scala#L521
//            case Thrown(e) => {
//              // NOTE (2013-10-30, jdonmez/msiegel): fixes closing resources on error, and is NOT present
//              //   in later version linked above. Should we submit a pull request against Play master?
//              onError(e.getMessage, Input.Empty)
//              // fixes propagation of errors to downstream iteratee, and is present in later version linked above
//              iterateeP.throwing(e)
//            }
//            case _ => onComplete()
//          }
//
//        }
//
//        step(it)
//        iterateeP
//      }
//    }
//
//  }
//
//}