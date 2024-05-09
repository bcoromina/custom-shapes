package shapes

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class LimitedQueue[A](maxSize: Int) extends mutable.Queue[A] {
  override def enqueue(elem: A): this.type = {
    if (length >= maxSize) dequeue()
    enqueue(elem);
    this
  }
  def used: Int = size
}

case class MapAsyncUnordered[In, Out](parallelism: Int, f: In => Future[Out])
  extends GraphStage[FlowShape[In, Out]] {

  private val in = Inlet[In]("MapAsyncUnordered.in")
  private val out = Outlet[Out]("MapAsyncUnordered.out")

  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def toString = s"MapAsyncUnordered.Logic(inFlight=$inFlight, buffer=$buffer)"

      lazy val decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var inFlight = 0
      private var buffer: LimitedQueue[Out] = _
      private val invokeFutureCB: Try[Out] => Unit = getAsyncCallback(futureCompleted).invoke

      private[this] def todo: Int = inFlight + buffer.used

      override def preStart(): Unit = buffer = new LimitedQueue[Out](parallelism)

      def futureCompleted(result: Try[Out]): Unit = {
        def isCompleted = isClosed(in) && todo == 0
        inFlight -= 1
        result match {
          case Success(elem) if elem != null =>
            if (isAvailable(out)) {
              if (!hasBeenPulled(in)) tryPull(in)
              push(out, elem)
              if (isCompleted) completeStage()
            } else buffer.enqueue(elem)
          case Success(_) =>
            if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
          case Failure(ex) =>
            if (decider(ex) == Supervision.Stop) failStage(ex)
            else if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
        }
      }

      override def onPush(): Unit = {
        try {
          val future = f(grab(in))
          inFlight += 1
          future.value match {
            case None    => future.onComplete(invokeFutureCB)(ExecutionContext.parasitic)
            case Some(v) => futureCompleted(v)
          }
        } catch {
          case NonFatal(ex) => if (decider(ex) == Supervision.Stop) failStage(ex)
        }
        if (todo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (todo == 0) completeStage()
      }

      override def onPull(): Unit = {
        if (buffer.nonEmpty) push(out, buffer.dequeue())

        val leftTodo = todo
        if (isClosed(in) && leftTodo == 0) completeStage()
        else if (leftTodo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      setHandlers(in, out, this)
    }
}