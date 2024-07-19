/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(factory = DefaultPingExecutor.FACTORY_NAME, service = PingExecutor.class)
public final class DefaultPingExecutor implements PingExecutor, AutoCloseable {
    private static final class Process extends AbstractRegistration implements Runnable {
        private final Runnable task;
        private final ScheduledFuture<?> future;

        Process(final Runnable task, final ScheduledThreadPoolExecutor threadPool, final long delay,
                final TimeUnit timeUnit) {
            this.task = requireNonNull(task);
            future = threadPool.scheduleWithFixedDelay(task, delay, delay, timeUnit);
        }

        @Override
        protected void removeRegistration() {
            future.cancel(false);
        }

        @Override
        public void run() {
            if (notClosed()) {
                task.run();
            }
        }
    }

    public static final String DEFAULT_NAME_PREFIX = "ping-executor";
    public static final int DEFAULT_CORE_POOL_SIZE = 1;
    public static final String FACTORY_NAME = "org.opendaylight.restconf.nb.rfc8040.streams.DefaultPingExecutor";
    private static final String PROP_NAME_PREFIX = ".namePrefix";
    private static final String PROP_CORE_POOL_SIZE = ".corePoolSize";

    // FIXME: Java 21: just use thread-per-task executor with virtual threads
    private final ScheduledThreadPoolExecutor threadPool;

    public DefaultPingExecutor(final String namePrefix, final int corePoolSize) {
        final var counter = new AtomicLong();
        final var group = new ThreadGroup(requireNonNull(namePrefix));
        threadPool = new ScheduledThreadPoolExecutor(corePoolSize,
            target -> new Thread(group, target, namePrefix + '-' + counter.incrementAndGet()));
    }

    @Inject
    public DefaultPingExecutor() {
        this(DEFAULT_NAME_PREFIX, DEFAULT_CORE_POOL_SIZE);
    }

    @Activate
    public DefaultPingExecutor(final Map<String, ?> props) {
        this((String) props.get(PROP_NAME_PREFIX), (int) requireNonNull(props.get(PROP_CORE_POOL_SIZE)));
    }

    @Override
    public Registration startPingProcess(final Runnable task, final long delay, final TimeUnit timeUnit) {
        return new Process(task, threadPool, delay, timeUnit);
    }

    @Override
    @Deactivate
    @PreDestroy
    public void close() {
        threadPool.shutdown();
    }

    public static Map<String, ?> props(final String namePrefix, final int corePoolSize) {
        return Map.of(PROP_NAME_PREFIX, namePrefix, PROP_CORE_POOL_SIZE, corePoolSize);
    }
}
