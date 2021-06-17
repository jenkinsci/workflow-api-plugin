/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.jenkinsci.plugins.workflow.flow;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory and utility methods for {@link java.util.concurrent.Executor}, {@link ExecutorService},
 * and {@link java.util.concurrent.ThreadFactory}.
 *
 * @author Eric Fellheimer
 * @author Kyle Littlefield
 * @author Justin Mahoney
 * @since 3.0
 */
@Restricted(NoExternalUse.class)
public final class MoreExecutors {
  private MoreExecutors() {}

  /**
   * Returns an {@link Executor} that runs each task in the thread that invokes {@link
   * Executor#execute execute}, as in {@code ThreadPoolExecutor.CallerRunsPolicy}.
   *
   * <p>This executor is appropriate for tasks that are lightweight and not deeply chained.
   * Inappropriate {@code directExecutor} usage can cause problems, and these problems can be
   * difficult to reproduce because they depend on timing. For example:
   *
   * <ul>
   *   <li>A call like {@code future.transform(function, directExecutor())} may execute the function
   *       immediately in the thread that is calling {@code transform}. (This specific case happens
   *       if the future is already completed.) If {@code transform} call was made from a UI thread
   *       or other latency-sensitive thread, a heavyweight function can harm responsiveness.
   *   <li>If the task will be executed later, consider which thread will trigger the execution --
   *       since that thread will execute the task inline. If the thread is a shared system thread
   *       like an RPC network thread, a heavyweight task can stall progress of the whole system or
   *       even deadlock it.
   *   <li>If many tasks will be triggered by the same event, one heavyweight task may delay other
   *       tasks -- even tasks that are not themselves {@code directExecutor} tasks.
   *   <li>If many such tasks are chained together (such as with {@code
   *       future.transform(...).transform(...).transform(...)....}), they may overflow the stack.
   *       (In simple cases, callers can avoid this by registering all tasks with the same {@code
   *       MoreExecutors#newSequentialExecutor} wrapper around {@code directExecutor()}. More
   *       complex cases may require using thread pools or making deeper changes.)
   * </ul>
   *
   * Additionally, beware of executing tasks with {@code directExecutor} while holding a lock. Since
   * the task you submit to the executor (or any other arbitrary work the executor does) may do slow
   * work or acquire other locks, you risk deadlocks.
   *
   * <p>This instance is equivalent to:
   *
   * <pre>{@code
   * final class DirectExecutor implements Executor {
   *   public void execute(Runnable r) {
   *     r.run();
   *   }
   * }
   * }</pre>
   *
   * <p>This should be preferred to {@code #newDirectExecutorService()} because implementing the
   * {@link ExecutorService} subinterface necessitates significant performance overhead.
   *
   *
   * @since 18.0
   */
  public static Executor directExecutor() {
    return DirectExecutor.INSTANCE;
  }
}
