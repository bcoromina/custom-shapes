package shapes

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

class MapAsyncUnorderedFlowSpec extends ScalaTestWithActorTestKit() with AnyFlatSpecLike  with BeforeAndAfterAll {

  implicit val ec = system.executionContext
  val numElements = 5

  it should "map elements concurrently" in {

    val numRunning = new AtomicInteger(0)

    val stringTrx: Int => String = i => s"Processed $i"

    val allRunning = Promise[Unit]

    val asyncFlowShape = MapAsyncUnordered[Int, String](parallelism = numElements, (i: Int) =>  Future {
      if(numRunning.incrementAndGet() == numElements){
        allRunning.complete(Try{()})
      }
      Await.result(allRunning.future, 4.seconds)
      stringTrx(i)
    })

    val asyncFlow = Flow.fromGraph(asyncFlowShape)

    def strCompose: (String, String) => String = (a,b) =>  a + ", " + b

    val sinkfold: Sink[String, Future[String]] = Sink.fold("")( strCompose )

    val res = Source(1 to numElements).via(asyncFlow).runWith(sinkfold).futureValue

    (1 to numElements).map(stringTrx).toList.forall(res.contains) shouldBe true
  }

  ignore should "map elements with exception" in {

    val exceptionMessage = "number 3 causes exception"

    val numElements = 5
    val numRunning = new AtomicInteger(0)

    val stringTrx: Int => String = i => s"Processed $i"

    val allRunning = Promise[Unit]

    val asyncFlowShape = MapAsyncUnordered[Int, String](parallelism = numElements, (i: Int) =>  Future {

      val numElem = numRunning.incrementAndGet()

      if(numElem == numElements){
        allRunning.complete(Try{()})
      } else if (numElem ==3 ){
        throw new Exception(exceptionMessage + "sd")
      }
      Await.result(allRunning.future, 4.seconds)
      stringTrx(i)
    })

    val asyncFlow = Flow.fromGraph(asyncFlowShape)

    def strCompose: (String, String) => String = (a,b) =>  a + ", " + b

    val sinkfold: Sink[String, Future[String]] = Sink.fold("")( strCompose )

    val res = Source(1 to numElements).via(asyncFlow).runWith(sinkfold)

    res.onComplete {
      case Failure(exception) =>
        exception.getMessage shouldBe exceptionMessage
      case Success(value) =>
        fail()
    }

  }


  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}