package semaphore;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SemaphoreImpl implements Semaphore {
	// volatile: http://www.javamex.com/tutorials/synchronization_volatile.shtml
	// volatile wird eig. nur für available() benötigt?
	private volatile int value; // guardedBy("lock")
	private LinkedList<Thread> threads; // guardedBy("lock")
	
	private Lock lock;

	public SemaphoreImpl(int initial) {
		if (initial < 0) throw new IllegalArgumentException();
		value = initial;
		threads = new LinkedList<Thread>();
		
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
			
			// wir tragen uns immer in die Liste ein, um sicherzustellen dass nicht
			// nur value > 0 ist, sondern wir auch tatsächlich an der Reihe sind
			Thread t = Thread.currentThread();
			threads.addLast(t);
			
			while (!(value > 0 && t == threads.peekFirst())) {
				try {
					lock.unlock();
					synchronized (t) {
						t.wait();
					}
				} catch (InterruptedException e) { }
				lock.lock();
			}
			--value;
			if (t != threads.removeFirst()) {
				throw new IllegalStateException("Thread " + t.getName() +
						" left acquire() but wasnt first in queue");
			}
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
				Thread next = threads.peekFirst();
				synchronized (next) {
					next.notify();
				}
			}
		} finally {
			lock.unlock();
		}
	}
}
