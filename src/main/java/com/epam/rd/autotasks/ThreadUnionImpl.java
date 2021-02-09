package com.epam.rd.autotasks;

import java.util.ArrayList;
import java.util.List;

public class ThreadUnionImpl implements ThreadUnion {
    private static final String NAME_PATTERN = "%s-worker-%d";
    private final String name;
    private final List<Thread> threads = new ArrayList<>();
    private final List<FinishedThreadResult> results = new ArrayList<>();
    private int totalSize;
    private boolean isShutdown;

    public ThreadUnionImpl(final String name) {
        this.name = name;
    }

    @Override
    public int totalSize() {
        return totalSize;
    }

    @Override
    public int activeSize() {
        return (int) threads.stream().filter(Thread::isAlive).count();
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        threads.forEach(Thread::interrupt);
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public void awaitTermination() {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isFinished() {
        return isShutdown && activeSize() == 0;
    }

    @Override
    public List<FinishedThreadResult> results() {
        return results;
    }

    @Override
    public synchronized Thread newThread(final Runnable r) {
        checkShutdown();
        Thread thread = new ThreadSendingResults(r);
        thread.setName(String.format(NAME_PATTERN, name, totalSize++));
        thread.setUncaughtExceptionHandler((t, e) -> {
            synchronized (results) {
                results.add(new FinishedThreadResult(t.getName(), e));
            }
        });
        threads.add(thread);
        return thread;
    }

    private void checkShutdown() {
        if (isShutdown) {
            throw new IllegalStateException("Thread union is shutdown");
        }
    }

    private class ThreadSendingResults extends Thread {
        public ThreadSendingResults(final Runnable target) {
            super(target);
        }

        @Override
        public void run() {
            super.run();
            synchronized (results) {
                if (notInResultsYet()) {
                    results.add(new FinishedThreadResult(this.getName()));
                }
            }
        }

        private boolean notInResultsYet() {
            return results.stream()
                    .noneMatch(r -> r.getThreadName().equals(this.getName()));
        }
    }
}
