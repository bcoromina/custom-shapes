package shapes

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._
import shapes.util.LimitedQueue

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class MapAsyncErrorUnordered[In, Out](parallelism: Int, f: In => Future[Out])
  extends GraphStage[FanOutShape2[In, Out, Throwable]] {

  private val in = Inlet[In]("MapAsyncUnordered.in")
  private val out1 = Outlet[Out]("MapAsyncUnordered.out_Ok")
  private val out2 = Outlet[Throwable]("MapAsyncUnordered.out_Error")

  override val shape = new FanOutShape2(in, out1, out2)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def toString = s"MapAsyncUnordered.Logic(inFlight=$inFlight, buffer=$buffer, bufferErr=$bufferErr)"

      lazy val decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider

      private var inFlight = 0
      private var buffer: LimitedQueue[Out] = _
      private var bufferErr: LimitedQueue[Throwable] = _
      private val invokeFutureCB: Try[Out] => Unit = getAsyncCallback(futureCompleted).invoke

      private[this] def todo: Int = inFlight + buffer.used + bufferErr.used

      override def preStart(): Unit = {
        buffer = new LimitedQueue[Out](parallelism)
        bufferErr = new LimitedQueue[Throwable](parallelism)
      }

      def futureCompleted(result: Try[Out]): Unit = {
        def isCompleted = isClosed(in) && todo == 0
        inFlight -= 1
        result match {
          case Success(elem) if elem != null =>
            if (isAvailable(out1)) {
              if (!hasBeenPulled(in)) tryPull(in)
              push(out1, elem)
              if (isCompleted) completeStage()
            } else buffer.enqueue(elem)
          case Success(_) =>
            if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
          case Failure(ex) =>
            if (isAvailable(out2)) {
              if (!hasBeenPulled(in)) tryPull(in)
              push(out2, ex)
              if (isCompleted) completeStage()
            } else bufferErr.enqueue(ex)
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
        if (buffer.nonEmpty) push(out1, buffer.dequeue())
        if (bufferErr.nonEmpty) push(out2, bufferErr.dequeue())

        val leftTodo = todo
        if (isClosed(in) && leftTodo == 0) completeStage()
        else if (leftTodo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      setHandler(in, this)
      setHandler(out1, this)
      setHandler(out2, this)

    }
}