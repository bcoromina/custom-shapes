package shapes

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.Future

class MapAsyncFlow[A, B](parallelism: Int, f: A => Future[B]) extends GraphStage[FlowShape[A, B]] {

  val in: Inlet[A] = Inlet("MapAsyncFlow.in")
  val out: Outlet[B] = Outlet("MapAsyncFlow.out")

  override val shape: FlowShape[A, B] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var inFlight = 0

    // Called when an element is available at the input port
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val elem = grab(in)
        val future = f(elem)

        // Increment the inFlight counter
        inFlight += 1

        future.onComplete { result =>
          // Decrement the inFlight counter
          inFlight -= 1
          if (isAvailable(out)) {
            pushElementIfAvailable()
          }
        }(materializer.executionContext)

        // Suspend the port until we have capacity
        if (inFlight < parallelism) {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (inFlight == 0) {
          completeStage()
        }
      }
    })

    // Called when downstream requests an element
    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pushElementIfAvailable()
      }
    })

    private def pushElementIfAvailable(): Unit = {
      if (inFlight == 0 && isClosed(in)) {
        // If there are no more in-flight elements and the input port is closed, complete the output port
        complete(out)
      } else if (inFlight < parallelism && !hasBeenPulled(in)) {
        pull(in)
      }
    }
  }
}