package semaphore;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SemaphoreImpl implements Semaphore {
	// volatile: http://www.javamex.com/tutorials/synchronization_volatile.shtml
	private volatile int value;
	private LinkedList<Thread> threads;
	private Lock valLock;

	public SemaphoreImpl(int initial) {
		if (initial < 0) throw new IllegalArgumentException();
		value = initial;
		threads = new LinkedList<>();
		valLock = new ReentrantLock();
	}

	@Override
	public int available() {
		return value;
	}

	@Override
	public void acquire() {		
		
	}

	@Override
	public void release() {

	}
}
