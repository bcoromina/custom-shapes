package shapes

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.concurrent.{Await, Future}

class MapAsyncFlowSpec extends ScalaTestWithActorTestKit() with AnyFlatSpecLike  with BeforeAndAfterAll {

  implicit val ec = system.executionContext
  implicit val classicSystem = system.classicSystem

  it should "map elements" in {




    val asyncFlowShape = MapAsyncUnordered[Int, String](parallelism = 3, (i: Int) =>  Future{
      Thread.sleep(500 + i * 100) // Simulate async operation
      s"Processed $i"
    })

    val asyncFlow = Flow.fromGraph(asyncFlowShape)


    val probe: TestProbe[String] = createTestProbe[String]()

    //Sink.actorRef(probe.ref, onCompleteMessage = "completed", onFailureMessage = _ => "failed")
    //val sink: Sink[String, NotUsed] = Sink.actorRef(probe.ref, "last messege", _ => ())


    val sinkfold: Sink[String, Future[String]] = Sink.fold("")( _ + ", " + _ )
    val flow = Flow[String].map(e =>
      s"Consumed: $e"
    )


    val res = Source(1 to 5).via(asyncFlow).via(flow).runWith(sinkfold)

    val lr = Await.result(res, 60.seconds)

    res


    /*
    probe.expectMessage(1.seconds, s"Processed 1")
    probe.expectMessage(1.seconds, s"Processed 2")
    probe.expectMessage(1.seconds, s"Processed 3")
    probe.expectMessage(1.seconds, s"Processed 4")
    probe.expectMessage(1.seconds, s"Processed 5")*/

  }


  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}