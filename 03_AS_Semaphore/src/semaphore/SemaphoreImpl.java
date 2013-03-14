package semaphore;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SemaphoreImpl implements Semaphore {
	// volatile: http://www.javamex.com/tutorials/synchronization_volatile.shtml
	private volatile int value;
	private LinkedList<Thread> threads;
	private Lock lock;

	public SemaphoreImpl(int initial) {
		if (initial < 0) throw new IllegalArgumentException();
		value = initial;
		threads = new LinkedList<>();
		
		// faires lock _nicht_ erwünscht
		lock = new ReentrantLock();
	}

	@Override
	public int available() {
		return value;
	}

	@Override
	public void acquire() {
		try {
			lock.lock();
			while (value < 1) {
				lock.lock();
				threads.addLast(Thread.currentThread());
				try {
					lock.unlock();
					Thread.currentThread().wait();
				} catch (InterruptedException e) { }
			}
			--value;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void release() {
		try {
			lock.lock();
			++value;
			if (!threads.isEmpty()) {
				Thread next = threads.pollFirst();
				synchronized (next) {
					next.notify();
				}
			}
		} finally {
			lock.unlock();
		}
	}
}
