/* 
 * DEFLATE library (Java)
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/deflate-library-java
 * https://github.com/nayuki/DEFLATE-library-Java
 */

package io.nayuki.deflate;

import org.checkerframework.checker.index.qual.GTENegativeOne;
import org.checkerframework.checker.index.qual.IndexOrHigh;
import org.checkerframework.checker.index.qual.LTEqLengthOf;

import java.io.*;


public final class MarkableFileInputStream extends InputStream {
	
	private final RandomAccessFile raf;
	private @GTENegativeOne long markPosition; // This is the position of the cursor. It's -1 at first.
	
	
	
	public MarkableFileInputStream(String path) throws FileNotFoundException {
		this(new File(path));
	}
	
	
	public MarkableFileInputStream(File file) throws FileNotFoundException {
		raf = new RandomAccessFile(file, "r");
		markPosition = -1;
	}
	
	
	
	public @GTENegativeOne int read() throws IOException { // Reads a byte and returns it, or return -1 for end of file
		return raf.read();
	}
	
	
	public @GTENegativeOne @LTEqLengthOf("#1") int read(byte[] b, @IndexOrHigh("#1") int off,
														@IndexOrHigh("#1") int len) throws IOException {
		// The return type should be within b bounds, as the buffer might overflow. It's -1 if we reached end of file
		// A single byte read is either the byte or -1 if it is the end of file
		// An offset and a length of bytes to read that are not in range of b would be silly.
		// off and len can be safely sent to the read function from RandomAccessFile with values equal to b.length.
		return raf.read(b, off, len);
	}
	
	
	public boolean markSupported() {
		return true;
	}
	
	@SuppressWarnings("index")
	public void mark(@GTENegativeOne int readLimit) {
		try {
			markPosition = raf.getFilePointer();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	// Normally after a reset(), rereading the same file section will yield the same bytes.
	// But this is not always true - e.g. due to concurrent writing. Thus this class does not
	// provide a hard guarantee for the mark()/reset() behavior like BufferedInputStream does.

	public void reset() {
		try {
			if(markPosition > -1)
			  raf.seek(markPosition);
			// This may be a bug. markPosition starts off as -1 and seek() method from RandomAccessFile throws an
			// exception if a negative argument is passed. Therefore, if we call this reset function immediately after
			// initialization, it would crash. I added the if statement because if we call this function immediately
			// after initialization, it wouldn't do anything as we have nothing to reset, the position of the cursor
			// is at the beginning already
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
