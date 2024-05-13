package shapes

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.Future

class MapAsyncErrorUnorderedSpec extends ScalaTestWithActorTestKit() with AnyFlatSpecLike  with BeforeAndAfterAll {
  implicit val ec = system.executionContext
  val numElements = 5


  def buildGraph[A,B](in: Source[A, NotUsed],
                 futureFunction: A => Future[B],
                 errorHandler: Throwable => B): Source[B, NotUsed] = {
    Source.fromGraph(
      GraphDSL.create() { implicit builder =>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val inputShape = builder.add(in)
        val flowFuture = builder.add(MapAsyncErrorUnordered(numElements, futureFunction))
        val errorHandlerFlow = builder.add(Flow[Throwable].map(errorHandler))
        val merge = builder.add(Merge[B](2))

        inputShape ~> flowFuture.in
        flowFuture.out0 ~> merge.in(0)
        flowFuture.out1 ~> errorHandlerFlow ~> merge.in(1)

        SourceShape(merge.out)
      }
    )
  }

  it should "outputs " in {

    def testFunction(i : Int): Future[String] =
      if(i % 2 == 0)
        Future.failed(new Exception(s"$i value not allowed"))
      else
        Future.successful(s"$i is OK")

    def errorHalndler(t: Throwable): String =
      if(t.getMessage.contains("value not allowed"))
        t.getMessage
      else
        "unable to handle this exception"

    val result = buildGraph(Source(1 to numElements), testFunction, errorHalndler).runWith(Sink.seq).futureValue

    result.toSet shouldBe Set(
      "1 is OK",
      "2 value not allowed",
      "3 is OK",
      "4 value not allowed",
      "5 is OK"
    )
  }
}
