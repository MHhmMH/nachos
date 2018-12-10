package nachos.vm;
import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		
		// clock algorithm 
		clockArray = new LinkedList<>();
		hand = clockArray.listIterator();
		// free swap page 
		freeSwap = new LinkedList<>();
		
		// locks we require when we apply page replace
		page_replace_lock = new Lock();
		find_replace_lock = new Lock();
		
		// the swap file we maintain to store the swap page
		swapFile = fileSystem.open("vmswapfile.swap", true);
		
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	
	public static void PageReplacement(TranslationEntry Page_need_replace)
	{
		// we maintain a circular list of pages
		if (!hand.hasNext())
		{
			hand = clockArray.listIterator();
		}
		TranslationEntry Page_to_replace = hand.next();
		VMKernel.find_replace_lock.acquire();
		while (!Page_to_replace.used)
		{
			if (!hand.hasNext())
			{
				hand = clockArray.listIterator();
			}
			Page_to_replace = hand.next();
		}
		// change valid bit because we replace this page
		Page_to_replace.valid = false;
		VMKernel.find_replace_lock.release();
		hand.set(Page_need_replace);
		Page_need_replace.ppn = Page_to_replace.ppn;
		// if we replace a dirty page we need to write it to swapfile on memory
		if (Page_to_replace.dirty)
		{
			byte[] Dirtypage = new byte[Processor.pageSize];
			System.arraycopy(Machine.processor(), Page_to_replace.ppn * Processor.pageSize, Dirtypage, 0, Processor.pageSize);
			// this is for the case when swapfile is initailized
			if (freeSwap.isEmpty())
			{
				Page_to_replace.vpn = swapFile.tell();
				swapFile.write(Dirtypage, 0,Dirtypage.length);
			}
			else
			{
				int spn = freeSwap.remove(0);
				Page_to_replace.vpn = spn;
				swapFile.write(spn,Dirtypage, 0,Dirtypage.length);
			}
		}
		
	}
	// array to used to in the clock replace algorithm
	protected static List<TranslationEntry>  clockArray;
	
	protected static ListIterator<TranslationEntry> hand;
	
	protected static List<Integer> freeSwap;
	
	protected static OpenFile swapFile;
	
	protected static Lock page_replace_lock;
	
	protected static Lock find_replace_lock;
	
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
}
