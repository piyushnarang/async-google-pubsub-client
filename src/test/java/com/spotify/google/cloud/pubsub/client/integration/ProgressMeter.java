/*
 * Copyright (c) 2011-2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spotify.google.cloud.pubsub.client.integration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.util.concurrent.MoreExecutors.getExitingScheduledExecutorService;

class ProgressMeter implements Runnable {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final long NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private final AtomicLong totalLatency = new AtomicLong();
  private final AtomicLong totalOperations = new AtomicLong();

  private final String unit;
  private final boolean reportLatency;

  private long startTime;
  private long lastRows;
  private long lastTime;
  private long lastLatency;

  private static final ScheduledExecutorService EXECUTOR = getExitingScheduledExecutorService(
      new ScheduledThreadPoolExecutor(1));

  public ProgressMeter(final String unit) {
    this(unit, false);
  }

  public ProgressMeter(final String unit, final boolean reportLatency) {
    this.unit = unit;
    this.reportLatency = reportLatency;
    this.startTime = System.nanoTime();
    this.lastTime = startTime;
    EXECUTOR.scheduleAtFixedRate(this, 1, 1, TimeUnit.SECONDS);
  }

  public void inc() {
    this.totalOperations.incrementAndGet();
  }

  public void inc(final long ops) {
    this.totalOperations.addAndGet(ops);
  }

  public void inc(final long ops, final long latency) {
    this.totalOperations.addAndGet(ops);
    this.totalLatency.addAndGet(latency);
  }

  @Override
  public void run() {
    final long now = System.nanoTime();
    final long totalOperations = this.totalOperations.get();
    final long totalLatency = this.totalLatency.get();

    final long deltaOps = totalOperations - lastRows;
    final long deltaTime = now - lastTime;
    final long deltaLatency = totalLatency - lastLatency;

    lastRows = totalOperations;
    lastTime = now;
    lastLatency = totalLatency;

    final long operations = (deltaTime == 0) ? 0 : (NANOS_PER_S * deltaOps) / deltaTime;
    final double avgLatency = (deltaOps == 0) ? 0 : deltaLatency / (NANOS_PER_MS * deltaOps);
    final long seconds = TimeUnit.NANOSECONDS.toSeconds(now - startTime);

    System.out.printf("%,4ds: %,12d %s/s.", seconds, operations, unit);
    if (reportLatency) {
      System.out.printf("    %,10.3f ms avg latency.", avgLatency);
    }
    System.out.printf("    (total: %,12d)\n", totalOperations);
    System.out.flush();
  }
}