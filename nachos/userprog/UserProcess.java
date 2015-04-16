package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

import java.io.EOFException;
import java.io.File;


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
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		fileDescriptors = new OpenFile[16];
		fileDescriptors[0] = UserKernel.console.openForReading();//stdin
		fileDescriptors[1] = UserKernel.console.openForWriting();//stdout
		systemWideOpenFiles.add(fileDescriptors[0].getName());
		systemWideOpenFiles.add(fileDescriptors[1].getName());
		
		processIdLock.acquire();
		processID = ProcessMap.size();
		ProcessMap.put(processID, this);
		processIdLock.release();
		
		exitStatus = 0;
		exitSem = new Semaphore(0);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
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

		new UThread(this).setName(name).fork();

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
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		////////////////////////////////////////////////
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		int firstPage = Processor.pageFromAddress(vaddr);
        int lastPage = Processor.pageFromAddress(vaddr + length);
        int amount = 0;

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || pageTable[lastPage].valid != true || Processor.makeAddress(pageTable[lastPage].ppn,0) >= memory.length)//??????????????
			return 0;
		
		for (int i = firstPage; i <= lastPage; i++){
			int startVAddr = Processor.makeAddress(i, 0);
			int endVAddr = startVAddr + (pageSize - 1);
			int pageOffsetStart;
			int pageOffsetEnd;

			if (pageTable[i].valid != true)
			{
				break;
			}

			//read to the end of the page
			if (vaddr + length >= endVAddr)
			{
				if (vaddr <= startVAddr) //read the entire page
				{
					pageOffsetStart = 0;
					pageOffsetEnd = pageSize - 1;
				}
				else
				{
					pageOffsetStart = vaddr - startVAddr;
					pageOffsetEnd = pageSize - 1;
				}
			}

			//read begin of page to not quite the end
			else if (vaddr <= startVAddr && vaddr + length < endVAddr)
			{
				pageOffsetStart = 0;
				pageOffsetEnd = (vaddr + length) - startVAddr;
			}

			//read part of page where offset is not aligned to beginning or end
			else
			{
				pageOffsetStart = vaddr - startVAddr;
				pageOffsetEnd = (vaddr + length) - startVAddr;
			}

			System.arraycopy(memory, Processor.makeAddress(pageTable[i].ppn, pageOffsetStart), data, offset + amount, pageOffsetEnd - pageOffsetStart);
			amount += (pageOffsetEnd - pageOffsetStart);

			pageTable[i].used = true;

		}


		return amount;
	}

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
		///////////////////////////////////////////////////
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		int firstPage = Processor.pageFromAddress(vaddr);
        int lastPage = Processor.pageFromAddress(vaddr + length);
        int amount = 0;

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || pageTable[firstPage].valid != true || Processor.makeAddress(pageTable[firstPage].ppn,0) >= memory.length)//??????????????
			return 0;
		
		for (int i = firstPage; i <= lastPage; i++){
			int startVAddr = Processor.makeAddress(i, 0);
			int endVAddr = startVAddr + (pageSize - 1);
			int pageOffsetStart;
			int pageOffsetEnd;

			if (pageTable[i].valid != true||pageTable[i].readOnly)
			{
				//Do not initiate write command if page is Read Only
				break;
			}

			//write to the end of the page
			if (vaddr + length >= endVAddr)
			{
				if (vaddr <= startVAddr)//write entire page
				{
					pageOffsetStart = 0;
					pageOffsetEnd = pageSize - 1;
				}
				else
				{
					pageOffsetStart = vaddr - startVAddr;
					pageOffsetEnd = pageSize - 1;
				}
			}

			//write begin of page to not quite the end
			else if (vaddr <= startVAddr && vaddr + length < endVAddr)
			{
				pageOffsetStart = 0;
				pageOffsetEnd = (vaddr + length) - startVAddr;
			}

			//write part of page where offset is not aligned to beginning or end
			else
			{
				pageOffsetStart = vaddr - startVAddr;
				pageOffsetEnd = (vaddr + length) - startVAddr;
			}

			System.arraycopy(data, offset + amount, memory, Processor.makeAddress(pageTable[i].ppn, pageOffsetStart), pageOffsetEnd - pageOffsetStart);

			amount += (pageOffsetEnd - pageOffsetStart);

			pageTable[i].used = true;
			pageTable[i].dirty = true;

		}


		return amount;
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
		//////////////////////////////////////
		
		int freePageIndex=findLocation(numPages);
		if (numPages > Machine.processor().getNumPhysPages()||freePageIndex==-1) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		
		for (int i = 0; i < numPages; i++)
        {
            int nextFreePage = UserKernel.freePhysPages.remove(UserKernel.freePhysPages.indexOf(freePageIndex + i));
            pageTable[i] = new TranslationEntry(i, nextFreePage, true, false, false, false);
        }

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		///////////////////////////////////////////
		coff.close();
		UserKernel.freePagesLock.acquire();
		
		for(int i=0; i < numPages; i++){
			UserKernel.freePhysPages.add(pageTable[i].ppn);
		}
		
		Collections.sort(UserKernel.freePhysPages);
		UserKernel.freePagesLock.release();
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

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		if(processID != 0) return 0;
		
		Machine.halt();
		
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	
	private int handleExit(int status)
	{
		exitStatus = status;
		//free fd
		for (int fd = 0; fd < 16; fd++)
		{
			handleClose(fd);
		}

		//free memory
		unloadSections();

		//exit
		if (processID == 0){
			Kernel.kernel.terminate();
		}
		else{
			UThread.finish();
		}
		exitSem.V();
		return status;
	}
	private int handleExec(int name, int argc, int argv)
	{
		boolean execLoaded;
		String execFile = readVirtualMemoryString(name, 255);
		if (execFile == null)
		{
			return -1;
		}
		else if (!execFile.endsWith(".coff") || argc < 0)
		{
			return -1;
		}

		//build arguments
		String[] args = new String[argc];
		byte[] data = new byte[argc*4];
		int datalength = readVirtualMemory(argv, data);
		if (datalength != data.length)
		{
			return -1;
		}

		for (int p=0 ; p < argc; p++)
		{
			int pointer = Lib.bytesToInt(data, p*4);
			args[p] = readVirtualMemoryString(pointer, 255);
		}

		//execution
		UserProcess newProcess = newUserProcess();

		newProcess.parent = this;

		saveState();

		execLoaded = newProcess.execute(execFile, args);
		if (execLoaded)
		{
			return newProcess.processID;
		}
		else
		{
			return -1;
		}
	}
	private int handleJoin(int id, int status)
	{
		UserProcess childProcess = ProcessMap.get(id);
		if (childProcess == null || childProcess.parent != this)
		{
			return -1;
		}
		ProcessMap.put(id, null);
		childProcess.parent = null;
		childProcess.exitSem.P();
		if (writeVirtualMemory(status, Lib.bytesFromInt(childProcess.exitStatus)) > 0)
		{
			return 1;
		}
		return -1;
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
		case syscallExit:
			return handleExit(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	public int handleCreate(int name){
		if(name < 0){
			Lib.debug(dbgProcess, "\t Negative virtual address name detected");
			return -1;
		}
		
		String fileName = readVirtualMemoryString(name, 256);
		if(fileName == null){
			Lib.debug(dbgProcess, "\t Null file name found");
			return -1;
		}
		
		for (int fdIndex = 0; fdIndex<16; fdIndex++){
			if(fileDescriptors[fdIndex] == null){
				fileDescriptors[fdIndex] = ThreadedKernel.fileSystem.open(fileName, true);
				systemWideOpenFiles.add(fileDescriptors[fdIndex].getName());
				return fdIndex;
			}else{
				if(fileDescriptors[fdIndex].getName().compareTo(fileName) == 0)
					return fdIndex;
			}
		}
		
		return -1;
	}
	
	public int handleOpen(int name){
		if(name < 0){
			Lib.debug(dbgProcess, "\t Negative virtual address name detected");
			return -1;
		}
		
		String fileName = readVirtualMemoryString(name, 256);
		if(fileName == null){
			Lib.debug(dbgProcess, "\t Null file name found");
			return -1;
		}
		
		for (int fdIndex = 0; fdIndex<16; fdIndex++){
			if(fileDescriptors[fdIndex] == null){
				fileDescriptors[fdIndex] = ThreadedKernel.fileSystem.open(fileName,	false);
				if(fileDescriptors[fdIndex] == null){
					return -1;
				}
				systemWideOpenFiles.add(fileDescriptors[fdIndex].getName());
				return fdIndex;
			}else{
				if(fileDescriptors[fdIndex].getName().compareTo(fileName) == 0){
					System.out.println("\t File already open");
					return fdIndex;
				}
			}
		}
		System.out.println("\t File opened exceed maximum concurrent file number (16)");
		return -1;
	}
	
	public int handleRead(int fd, int buffer, int size){
		int numberBytes = 0;
		
		if((fd < 0) || (fd > 15)){
			return -1;
		}else if(fileDescriptors[fd] == null){
			return -1;
		}
		if(size < 0){
			return -1;
		}
		
		byte byteBuffer[] = new byte[size];
		int pos=0, offset=0, length=size;
		numberBytes = fileDescriptors[fd].read(byteBuffer, offset, length);
		if((numberBytes < 0) || (numberBytes > size)){
			System.out.println("\t Read file error. ");
			return -1;
		}
		
		numberBytes = writeVirtualMemory(buffer, byteBuffer, offset, numberBytes);
		if((numberBytes < 0) || (numberBytes > size)){
			System.out.println("\t Write virtual memory error. ");
			return -1;
		}

		return numberBytes;
	}
	
	public int handleWrite(int fd, int buffer, int size){
		int numberBytes = 0;

		if((fd < 0) || (fd > 15)){
			return -1;
		}else if(fileDescriptors[fd] == null){
			return -1;
		}
		if(size < 0){
			return -1;
		}
		
		byte byteBuffer[] = new byte[size];
		int offset = 0;
		int length = size;
		numberBytes = readVirtualMemory(buffer, byteBuffer, offset, length);
		if((numberBytes<0) || (numberBytes>15)){
			System.out.println("\t Read data from virtual memory error. ");
			return -1;
		}
		
		numberBytes = fileDescriptors[fd].write(byteBuffer, offset, numberBytes);
		if((numberBytes < 0) || (numberBytes > 15)){
			System.out.println("\t Write data to file error. ");
			return -1;
		}
		
		return numberBytes;
	}
	
	public int handleClose(int fd){
		if((fd<0) || (fd>15)){
			return -1;
		}
		if(fileDescriptors[fd] == null)
			return -1;
		
		OpenFile file = fileDescriptors[fd];
		fileDescriptors[fd] = null;
		file.close();
		systemWideOpenFiles.remove(file.getName());
		
		return -1;
	}
	
	public int handleUnlink(int addr){
		if (addr < 0) return -1;
		String fileName = readVirtualMemoryString(addr, 256);
		if(fileName == null) return -1;
		
		for(int fd=0; fd<16; fd++){
			if(fileDescriptors[fd]!=null && fileName.compareTo(fileDescriptors[fd].getName())==0){
				fileDescriptors[fd].close();
				systemWideOpenFiles.remove(fileName);
				fileDescriptors[fd] = null;
			}
		}
		
		if(systemWideOpenFiles.contains(fileName)){
			unlinkedFiles.add(fileName);
			boolean returnValue = UserKernel.fileSystem.remove(fileName);
			if(!returnValue) return -1;
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
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}
	
	/**
     * Function to find a best location in Physical memory
     * to create a contiguous block 
     * (Make use of "gaps" in the free memory pool)
     * 
     * @param PagesNeeded number of Physical Pages needed by Process
     */
    private int findLocation(int PagesNeeded)
    {
    	///////////////////////////////////////////////////
        int startPage = 0;
        int count = 0;
        int endPage = 0;
        boolean blockFound = false;
        UserKernel.freePagesLock.acquire();

        startPage = UserKernel.freePhysPages.peekFirst();

        for (Integer freePage : UserKernel.freePhysPages)
        {
            if (freePage == startPage)
            {
                //initial count and endPage
                count = 1;
                endPage = freePage;
            }
            else if (count == PagesNeeded)
            {
                blockFound = true;
                break;
            }
            else if (freePage == (endPage + 1))
            {
                count++;
                endPage = freePage;
            }

            else
            {
                //Get to another gap in physical memory, reset startPage (Last gap doesn't fit the need)
                startPage = freePage;
                endPage = freePage;
                count = 1;
            }
        }

        UserKernel.freePagesLock.release();

        if (blockFound)
        {
            //Location is found, return startPage;
            return startPage;
        }
        else
        {
            return -1;
        }
    }


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
	
	//private final int processId;
	private static final Lock processIdLock=new Lock();
	//private static Map<Integer, UserProcess> allProcess = new HashMap<Integer, UserProcess>();
	
	private OpenFile fileDescriptors[];
	
	protected static LinkedList<String> systemWideOpenFiles = new LinkedList<String>();
	protected static LinkedList<String> unlinkedFiles = new LinkedList<String>();
	
	public int exitStatus = 0;
	private int processID;
	private static HashMap<Integer, UserProcess> ProcessMap = new HashMap<Integer, UserProcess>();
	private UserProcess parent;
	public Semaphore exitSem;
	
}
