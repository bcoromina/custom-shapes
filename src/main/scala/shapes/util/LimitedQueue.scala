package shapes.util

import scala.collection.mutable

class LimitedQueue[A](maxSize: Int) extends mutable.Queue[A] {
  override def enqueue(elem: A): this.type = {
    if (length >= maxSize) super.dequeue()
    super.enqueue(elem);
    this
  }
  def used: Int = size
}