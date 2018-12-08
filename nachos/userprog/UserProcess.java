package nachos.userprog;

import nachos.machine.*;

import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
//		int numPhysPages = Machine.processor().getNumPhysPages();
//		pageTable = new TranslationEntry[numPages];
//		for (int i = 0; i < numPhysPages; i++)
//			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		fileArray = new OpenFile[16];
		fileArray[0] = UserKernel.console.openForReading();
		fileArray[1] = UserKernel.console.openForWriting();
		childProcess = new HashMap<>();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		(this.thread = new UThread(this)).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	c
	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int res = 0;
		
		while(length > 0) {
			int vpn = vaddr/pageSize;
			int off = vaddr%pageSize;
			
			//vpn not exist in pagetable
			if(vpn<0 || vpn>pageTable.length) {
				return 0;
			}
			if(vpn == pageTable.length) {
				break;
			}
			
			if(pageTable[vpn].readOnly) {
				return 0;
			}
			int ppn = pageTable[vpn].ppn;
			int paddr = ppn*pageSize+off;
			
			int amount = Math.min(length, pageSize - off);
			System.arraycopy(data, offset, memory, paddr, amount);
			
			res = res+amount;
			length = length-amount;
			vaddr = vaddr+amount;
			offset = offset+amount;
		}
		
		return res;
		
		
		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(data, offset, memory, vaddr, amount);
//
//		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		
		UserKernel.FPP.acquire();
		
		if (numPages > UserKernel.freePhysicalPage.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			int ppn = UserKernel.freePhysicalPage.remove(UserKernel.freePhysicalPage.size()-1);
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}
		
		UserKernel.FPP.release();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				// for now, just assume virtual addresses=physical addresses
				pageTable[vpn].readOnly = section.isReadOnly();
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
//				section.loadPage(i, vpn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for(int i=0;i<pageTable.length;i++) {
			if(pageTable[i].valid) {
				UserKernel.freePhysicalPage.add(pageTable[i].ppn);
			}
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}
	
	
	private int handleCreate(int vaddr) {
		String fileName = readVirtualMemoryString(vaddr, 256);
		for(int i=2;i<fileArray.length;i++) {
			if(fileArray[i] == null) {
				OpenFile fileEntity = ThreadedKernel.fileSystem.open(fileName, true);
				fileArray[i]=fileEntity;
				return i;
			}
		}
		return -1;
	}
	
	private int handleOpen(int vaddr) {
		String fileName = readVirtualMemoryString(vaddr, 256);
		for(int i=2;i<fileArray.length;i++) {
			if(fileArray[i] == null) {
				OpenFile fileEntity = ThreadedKernel.fileSystem.open(fileName, false);
				if(fileEntity == null) {
					return -1;
				}
				fileArray[i]=fileEntity;
				return i;
			}
		}
		return -1;
	}
	
	private int handleRead(int fileDescriptor, int vaddr, int count) {
		if(fileDescriptor<0 || fileDescriptor>fileArray.length || count<0) {
			return -1;
		}
		
		int res = 0;
		OpenFile fileEntity = fileArray[fileDescriptor];
		if(fileEntity == null) {
			return -1;
		}
		
		
		while(count > 0) {
			byte[] buffer = new byte[1024];
			int numBytesRead = fileEntity.read(buffer, 0, Math.min(count, buffer.length));
			if(numBytesRead == -1 || numBytesRead != Math.min(count, buffer.length)) {
				return -1;
			}
			if(numBytesRead == 0) {
				break;
			}
			
			int numBytesWrite = writeVirtualMemory(vaddr, buffer, 0, numBytesRead);
			if(numBytesWrite == -1 || numBytesWrite != numBytesRead) {
				return -1;
			}
			
			res = res+numBytesWrite;
			count = count-numBytesWrite;
			vaddr = vaddr+numBytesWrite;
			
		}
		return res;
	}
	
	private int handleWrite(int fileDescriptor, int vaddr, int count) {
		if(fileDescriptor<0 || fileDescriptor>fileArray.length || count<0) {
			return -1;
		}
		
		int res = 0;
		OpenFile fileEntity = fileArray[fileDescriptor];
		if(fileEntity == null) {
			return -1;
		}
		
		while(count > 0) {
			byte[] buffer = new byte[1024];
			int numBytesRead = readVirtualMemory(vaddr, buffer, 0, Math.min(count, buffer.length));
			if(numBytesRead == -1 || numBytesRead != Math.min(count, buffer.length)) {
				return -1;
			}
			
			int numBytesWrite = fileEntity.write(buffer, 0, numBytesRead);
			if(numBytesWrite == -1 || numBytesWrite != numBytesRead) {
				return -1;
			}
			
			res = res+numBytesWrite;
			count = count-numBytesWrite;
			vaddr = vaddr+numBytesWrite;
		}
		return res;
	}
	
	private int handleClose(int fileDescriptor) {
		if(fileDescriptor<0 || fileDescriptor>fileArray.length || fileArray[fileDescriptor] == null) {
			return -1;
		}
		
		fileArray[fileDescriptor].close();
		fileArray[fileDescriptor] = null;
		return 0;
		
	}
	
	private int handleUnlink(int vaddr) {
		String fileName = readVirtualMemoryString(vaddr, 256);
		if(fileName.isEmpty()) {
			return -1;
		}
		boolean res = ThreadedKernel.fileSystem.remove(fileName);
		if(!res) return -1;
		else return 0;
	}
	
	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		
		if(pid != 0) {
			return 0;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	
	private int handleExec(int vaddr0, int argc, int vaddr1) {
		//handle invalid argc
		if(argc < 0) {
			return -1;
		}
		String fileName = readVirtualMemoryString(vaddr0, 256);
		if(fileName.isEmpty() || !fileName.endsWith(".coff")) {
			return -1;
		}
		
		//may have 0 length array
		byte[] pointers = new byte[argc*4];
		int readBytes = readVirtualMemory(vaddr1, pointers);
		if(readBytes != pointers.length) {
			return -1;
		}
		
		String[] arguments = new String[argc];
		for(int i=0;i<argc;i++) {
			int pos = pointers[i*4+3]<<24 + pointers[i*4+2]<<16 + pointers[i*4+1]<<8 + pointers[i*4+0];
			arguments[i] = readVirtualMemoryString(pos, 256);
		}
		
		UserProcess child = newUserProcess();
		child.parent = this;
		childProcess.put(child.pid, child);
		
		child.execute(fileName, arguments);
		return child.pid;
	}
	
	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		
		unloadSections();
		
		UserKernel.NP.acquire();
		
		this.status = status;
		this.isExitNormal = true;
		UserKernel.numProcess--;
		
		//delete any relations
		for(int key: childProcess.keySet()) {
			childProcess.get(key).parent = null;
		}
		childProcess = null;
		this.parent = null;
		
		//close any files
		for(OpenFile file: fileArray) {
			if(file != null) {
				file.close();
			}
		}
		
		if(UserKernel.numProcess == 0) {
			UserKernel.NP.release();
			Kernel.kernel.terminate();
		}
		else {
			UserKernel.NP.release();
			this.thread.finish();
		}
		
		return status;
	}
	
	protected void join() {
		this.thread.join();
	}
	
	private int handleJoin(int processID, int vaddr) {
		if(!childProcess.containsKey(processID)) {
			return -1;
		}
		UserProcess child = childProcess.get(processID);
		child.join();
		
		//disown child process
		childProcess.remove(processID);
		
		
		if(child.isExitNormal) {
			byte[] buffer = new byte[4];
			buffer[0] = (byte) (0xFF & child.status>>>0);
			buffer[1] = (byte) (0xFF & child.status>>>8);
			buffer[2] = (byte) (0xFF & child.status>>>16);
			buffer[3] = (byte) (0xFF & child.status>>>24);
			int writeBytes = writeVirtualMemory(vaddr, buffer);
			if(writeBytes != 4) {
				return -1;
			}
			return 1;
		}
		else {
			return 0;
		}
		
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			
			unloadSections();
			
			UserKernel.NP.acquire();
			
			this.isExitNormal = false;
			UserKernel.numProcess--;
			
			//delete any relations
			for(int key: childProcess.keySet()) {
				childProcess.get(key).parent = null;
			}
			childProcess = null;
			this.parent = null;
			
			//close any files
			for(OpenFile file: fileArray) {
				if(file!=null) {
					file.close();
				}
			}
			
			if(UserKernel.numProcess == 0) {
				UserKernel.NP.release();
				Kernel.kernel.terminate();
			}
			else {
				UserKernel.NP.release();
				this.thread.finish();
			}
			
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}
	
	protected OpenFile[] fileArray;
	
	protected UserProcess parent;
	
	protected Map<Integer, UserProcess> childProcess;
	
	//may have a old joined process
	
	protected int pid = processId++;
	
	protected static int processId = 0;
	
	protected boolean isExitNormal;
	
	protected int status;
	
	protected UThread thread;
	
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
