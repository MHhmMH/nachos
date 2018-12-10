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
		
		while(length > 0) 
		{
			int vpn = vaddr / pageSize;
			int off = vaddr % pageSize;
			VMKernel.find_replace_lock.acquire();
			pageTable[vpn].used = false;
			VMKernel.find_replace_lock.release();
			
			//vpn not exist in pagetable
			if(vpn < 0 || vpn > pageTable.length) 
			{
				return 0;
			}
			if(vpn == pageTable.length) 
			{
				break;
			}
			
			if (!pageTable[vpn].valid)
			{
			   this.handlePageFault(vpn*pageSize);
			}
			
			int ppn = pageTable[vpn].ppn;
			int paddr = ppn* pageSize + off;
			int amount = Math.min(length, pageSize - off);
			System.arraycopy(memory, paddr, data, offset, amount);
			
			VMKernel.find_replace_lock.acquire();
			pageTable[vpn].used = true;
			VMKernel.find_replace_lock.release();
			
			res = res + amount;
			length = length - amount;
			vaddr = vaddr + amount;
			offset = offset + amount;
		}
		
		return res;

	}

	/**
	 * Overwrite writeVirtualMemory.
	 */
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) 
	{
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int res = 0;
		
		while(length > 0) 
		{
			int vpn = vaddr / pageSize;
			int off = vaddr % pageSize;
			VMKernel.find_replace_lock.acquire();
			pageTable[vpn].used = false;
			VMKernel.find_replace_lock.release();
			
			//vpn not exist in pagetable
			if(vpn < 0 || vpn > pageTable.length) 
			{
				return 0;
			}
			if(vpn == pageTable.length) 
			{
				break;
			}
			
			if(pageTable[vpn].readOnly) 
			{
				return 0;
			}
			if (!pageTable[vpn].valid)
			{
			   this.handlePageFault(vpn*pageSize);
			}
			int ppn = pageTable[vpn].ppn;
			int paddr = ppn * pageSize + off;
			
			int amount = Math.min(length, pageSize - off);
			System.arraycopy(data, offset, memory, paddr, amount);
			
			VMKernel.find_replace_lock.acquire();
			pageTable[vpn].used = true;
			VMKernel.find_replace_lock.release();
			
			res = res + amount;
			length = length - amount;
			vaddr = vaddr + amount;
			offset = offset + amount;
		}
		
		return res;
	}
	
	protected boolean loadSections() {
		UserKernel.FPP.acquire();
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) 
		{
			pageTable[i] = new TranslationEntry(i, 0, false, false, false, false);
		}
		UserKernel.FPP.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < pageTable.length;i++) {
			VMKernel.page_replace_lock.acquire();
			// free all the valid page in memory
			if (pageTable[i].valid)
			{	
				pageTable[i].valid = false;
				// add this page to free page
				VMKernel.freePhysicalPage.add(0,pageTable[i].ppn);
				int remove_page_index = VMKernel.clockArray.indexOf(pageTable[i]);
				VMKernel.hand = VMKernel.clockArray.listIterator(remove_page_index);
				VMKernel.hand.next();
				VMKernel.hand.remove();
			}
			else
			{
				if (pageTable[i].dirty)
				{
					// add dirty page to swap 
					int can_swap = pageTable[i].vpn;
					VMKernel.freeSwap.add(can_swap);
				}
			}
			VMKernel.page_replace_lock.release();
		}
	}
	
	protected void handlePageFault(int vaddr) {
		int vpn = vaddr/pageSize;
		
		VMKernel.page_replace_lock.acquire();
		
		if(UserKernel.freePhysicalPage.isEmpty()) {
			VMKernel.PageReplacement(pageTable[vpn]);
		}
		else {
			int ppn = UserKernel.freePhysicalPage.remove(UserKernel.freePhysicalPage.size()-1);
			pageTable[vpn].ppn = ppn;
			VMKernel.hand.add(pageTable[vpn]);
		}
		
		pageTable[vpn].valid = true;
		
		VMKernel.page_replace_lock.release();
		
		if(pageTable[vpn].dirty) {
			byte[] buffer = new byte[pageSize];
			VMKernel.swapFile.read(pageTable[vpn].vpn, buffer, 0, pageSize);
			VMKernel.freeSwap.add(pageTable[vpn].vpn);
			writeVirtualMemory(vpn*pageSize, buffer);
		}
		else {
			if(vpn >= numPages-1-stackPages) {
				byte[] zero = {0};
				writeVirtualMemory(vpn*pageSize, zero);
			} else {
				for (int s = 0; s < coff.getNumSections(); s++) {
					CoffSection section = coff.getSection(s);
					if(vpn >= section.getFirstVPN() && vpn < section.getFirstVPN()+section.getLength()) {
						pageTable[vpn].readOnly = section.isReadOnly();
						section.loadPage(vpn-section.getFirstVPN(), pageTable[vpn].ppn);
						break;
					}
				}
			}
		}
		
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
		case Processor.exceptionPageFault:
			this.handlePageFault(processor.readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
