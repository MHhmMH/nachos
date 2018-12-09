package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Overwrite readVirtualMemory.
	 */
	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) 
	{
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int res = 0;
		int vpn = vaddr / pageSize;
		int off = vaddr % pageSize;
		VMKernel.find_replace_lock.acquire();
		pageTable[vpn].used = false;
		VMKernel.find_replace_lock.release();
		
		while(length > 0) {
			
			
			//vpn not exist in pagetable
			if(vpn < 0 || vpn > pageTable.length) {
				return 0;
			}
			if(vpn == pageTable.length) {
				break;
			}
			
			int ppn = pageTable[vpn].ppn;
			int paddr = ppn* pageSize+off;
			
			int amount = Math.min(length, pageSize - off);
			System.arraycopy(memory, paddr, data, offset, amount);
			
			res = res + amount;
			length = length - amount;
			vaddr = vaddr + amount;
			offset = offset + amount;
		}
		
		return res;

	}

	protected boolean loadSections() {
		return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
