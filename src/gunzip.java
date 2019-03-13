/* 
 * DEFLATE library (Java)
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/deflate-library-java
 * https://github.com/nayuki/DEFLATE-library-Java
 */

import io.nayuki.deflate.InflaterInputStream;
import io.nayuki.deflate.MarkableFileInputStream;
import org.checkerframework.checker.index.qual.IndexOrHigh;
import org.checkerframework.checker.index.qual.IndexOrLow;
import org.checkerframework.checker.index.qual.NonNegative;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.zip.CRC32;


/**
 * Decompression application for the gzip file format.
 * <p>Usage: java gunzip InputFile.gz OutputFile</p>
 * <p>This decompresses a single gzip file into a single output file. The program also prints
 * some information to standard output, and error messages if the file is invalid/corrupt.</p>
 */
public final class gunzip {
	
	public static void main(String[] args) {
		String msg = submain(args);
		if (msg != null) {
			System.err.println(msg);
			System.exit(1);
		}
	}
	
	@SuppressWarnings("cast") // Casting hexadecimal value 0x8B to a byte was a warning here. I don't know why
	// the checker issued a warning, as it really is 8-bits long (100010111)
	// Returns null if successful, otherwise returns an error message string.
	// There was another warning below, the cast of the return type of read method from FilterInputStream to
	// an index of the argument, which is redundant. The warnings were false positives.
	private static String submain(String[] args) {
		// Check arguments
		if (args.length != 2)
			return "Usage: java gunzip InputFile.gz OutputFile";
		
		File inFile = new File(args[0]);
		if (!inFile.exists())
			return "Input file does not exist: " + inFile;
		if (inFile.isDirectory())
			return "Input file is a directory: " + inFile;
		File outFile = new File(args[1]);
		
		try (DataInputStream din = new DataInputStream(new MarkableFileInputStream(inFile))) {
			// Read and process fixed-size header
			int flags;
			{
				byte[] b = new byte[10];
				din.readFully(b);
				if (b[0] != 0x1F || b[1] != (byte)0x8B)
					return "Invalid GZIP magic number";
				if (b[2] != 8)
					return "Unsupported compression method: " + (b[2] & 0xFF);
				flags = b[3] & 0xFF;
				
				// Reserved flags
				if ((flags & 0xE0) != 0)
					return "Reserved flags are set";
				
				// Modification time
				int mtime = (b[4] & 0xFF) | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | b[7] << 24;
				if (mtime != 0)
					System.err.println("Last modified: " + new Date(mtime * 1000L));
				else
					System.err.println("Last modified: N/A");
				
				// Extra flags
				switch (b[8] & 0xFF) {
					case 2:   System.err.println("Extra flags: Maximum compression");  break;
					case 4:   System.err.println("Extra flags: Fastest compression");  break;
					default:  System.err.println("Extra flags: Unknown (" + (b[8] & 0xFF) + ")");  break;
				}
				
				// Operating system
				String os;
				switch (b[9] & 0xFF) {
					case   0:  os = "FAT";             break;
					case   1:  os = "Amiga";           break;
					case   2:  os = "VMS";             break;
					case   3:  os = "Unix";            break;
					case   4:  os = "VM/CMS";          break;
					case   5:  os = "Atari TOS";       break;
					case   6:  os = "HPFS";            break;
					case   7:  os = "Macintosh";       break;
					case   8:  os = "Z-System";        break;
					case   9:  os = "CP/M";            break;
					case  10:  os = "TOPS-20";         break;
					case  11:  os = "NTFS";            break;
					case  12:  os = "QDOS";            break;
					case  13:  os = "Acorn RISCOS";    break;
					case 255:  os = "Unknown";         break;
					default :  os = "Really unknown";  break;
				}
				System.err.println("Operating system: " + os);
			}
			
			// Handle assorted flags and read more data
			{
				if ((flags & 0x01) != 0)
					System.err.println("Flag: Text");
				if ((flags & 0x04) != 0) {
					System.err.println("Flag: Extra");
					@NonNegative int len = readLittleEndianUint16(din); // The length can't be negative
					din.readFully(new byte[len]);  // Skip extra data
				}
				if ((flags & 0x08) != 0)
					System.err.println("File name: " + readNullTerminatedString(din));
				if ((flags & 0x02) != 0) {
					byte[] b = new byte[2];
					din.readFully(b);
					System.err.printf("Header CRC-16: %04X%n", (b[0] & 0xFF) | (b[1] & 0xFF) << 8);
				}
				if ((flags & 0x10) != 0)
					System.err.println("Comment: " + readNullTerminatedString(din));
			}
			
			// Start decompressing and writing output file
			long elapsedTime;
			OutputStream fout = new FileOutputStream(outFile);
			try {
				LengthCrc32OutputStream lcout = new LengthCrc32OutputStream(fout);
				InflaterInputStream iin = new InflaterInputStream(din, true);
				byte[] buf = new byte[64 * 1024];
				long startTime = System.nanoTime();
				while (true) {
					@IndexOrLow("buf") int n = (@IndexOrLow("buf") int) iin.read(buf);
					// n should be within buf range because we use it to access te array. It can also be -1, end of file
					// Another warning issued here for not casting iin.read(buf), but it is totally safe as read method
					// from FilterInputStream returns a number less than the parameter and greater or equal to -1
					if (n == -1)
						break;
					lcout.write(buf, 0, n);
				}
				elapsedTime = System.nanoTime() - startTime;
				System.err.printf("Input  speed: %.2f MiB/s%n",  inFile.length() / 1048576.0 / elapsedTime * 1.0e9);
				System.err.printf("Output speed: %.2f MiB/s%n", outFile.length() / 1048576.0 / elapsedTime * 1.0e9);
				
				// Process gzip footer
				iin.detach();
				if (lcout.getCrc32() != readLittleEndianInt32(din))
					return "Decompression CRC-32 mismatch";
				if ((int)lcout.getLength() != readLittleEndianInt32(din))
					return "Decompressed size mismatch";
			} finally {
				fout.close();
			}
			
		} catch (IOException e) {
			return "I/O exception: " + e.getMessage();
		}
		return null;
	}
	
	
	
	/*---- Helper methods and class ----*/
	
	private static String readNullTerminatedString(DataInput in) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		while (true) {
			byte b = in.readByte();
			if (b == 0)
				break;
			bout.write(b);
		}
		return new String(bout.toByteArray(), StandardCharsets.UTF_8);
	}
	
	@SuppressWarnings("cast") // The cast is safe because short range is 16 bits and we shift the result by 16 bits,
	// making the result non-negative if it happens to be negative.
	private static @NonNegative int readLittleEndianUint16(DataInput in) throws IOException {
		// This return statement is used in the previous method as an index, so it has to be non-negative.
		// The number is not negative because we shift the bits by 16 positions, and short range is exactly 16 bits
		return (@NonNegative int) (Integer.reverseBytes(in.readUnsignedShort()) >>> 16);
	}

	private static int readLittleEndianInt32(DataInput in) throws IOException {
		return Integer.reverseBytes(in.readInt());
	}
	
	
	
	private static final class LengthCrc32OutputStream extends FilterOutputStream {
		
		private @NonNegative long length;  // Total number of bytes written, modulo 2^64
		// The length can't be below 0
		private CRC32 checksum;
		
		
		public LengthCrc32OutputStream(OutputStream out) {
			super(out);
			length = 0;
			checksum = new CRC32();
		}
		
		
		public void write(int b) throws IOException {
			out.write(b);
			length++;
			checksum.update(b);
		}
		
		
		public void write(byte[] b, @IndexOrHigh("#1") int off, @IndexOrHigh("#1") int len) throws IOException {
			// The offset and the length should be within b range. According to write() in OutputStream and update()
			// in CRC32 class documentation, the offset and the length can be equal to the length of the b array.
			out.write(b, off, len);
			length += len;
			checksum.update(b, off, len);
		}
		
		
		public @NonNegative long getLength() { // Length makes sense only when non-negative
			return length;
		}
		
		
		public int getCrc32() {
			return (int)checksum.getValue();
		}
		
	}
	
}
