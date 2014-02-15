package executioncontext

import scala.concurrent.ExecutionContext
import java.util.{ArrayDeque, Deque}

object Execution {

  object Implicits {
    implicit def trampoline: ExecutionContext = Execution.trampoline
  }


  /**
   * Executes in the current thread. Uses a thread local trampoline to make sure the stack
   * doesn't overflow. Since this ExecutionContext executes on the current thread, it should
   * only be used to run small bits of fast-running code. We use it here to run the internal
   * iteratee code.
   *
   * Blocking should be strictly avoided as it could hog the current thread.
   * Also, since we're running on a single thread, blocking code risks deadlock.
   */
  val trampoline: ExecutionContext = new ExecutionContext {

    private val local = new ThreadLocal[Deque[Runnable]]

    def execute(runnable: Runnable): Unit = {
      @volatile var queue = local.get()
      if (queue == null) {
        // Since there is no local queue, we need to install one and
        // start our trampolining loop.
        try {
          queue = new ArrayDeque(4)
          queue.addLast(runnable)
          local.set(queue)
          while (!queue.isEmpty) {
            val runnable = queue.removeFirst()
            runnable.run()
          }
        } finally {
          // We've emptied the queue, so tidy up.
          local.set(null)
        }
      } else {
        // There's already a local queue that is being executed.
        // Just stick our runnable on the end of that queue.
        queue.addLast(runnable)
      }
    }

    def reportFailure(t: Throwable): Unit = t.printStackTrace()
  }

}
