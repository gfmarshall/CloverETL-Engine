/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.data;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *  Class implementing RecordBuffer backed by temporary file - i.e. unlimited
 *  size<br>
 *  Implements FIFO: push & shift operations can be interleaved, however it
 *  deteriorates performance.<br>
 *  It uses int pointers to datafile which means that the maximum size of the
 *  buffer is cca 4GB.
 *
 *@author     dpavlis
 *@created    21. kv�ten 2003
 *@since      December 09, 2002
 */
public class FileRecordBuffer {

	private FileChannel tmpFileChannel;
	private File tmpFile;
	private String tmpFilePath;

	private ByteBuffer dataBuffer;

	private int readPosition;
	private int writePosition;
	private int mapPosition;

	private boolean hasFile;
	private boolean isDirty;
	private boolean isClosed;
	// indicates whether buffer contains unwritten data

	// data
	private final static int DEFAULT_BUFFER_SIZE = Defaults.Record.MAX_RECORD_SIZE * 8;
	// size of BUFFER - used for push & shift operations
	private final static int LEN_SIZE_SPECIFIER = 4;
	// size of integer variable used to keep record length

	private final static String TMP_FILE_PREFIX = ".fbuf";
	// prefix of temporary file generated by system
	private final static String TMP_FILE_SUFFIX = ".tmp";
	// suffix of temporary file generated by system
	private final static String TMP_FILE_MODE = "rw";



	// methods
	/**
	 *  Constructor for the FileRecordBuffer object
	 *
	 *@param  tmpFilePath     Name of the subdirectory where to create TMP files or
	 *      NULL (the system default will be used)
	 *@param  dataBufferSize  The size of internal in memory buffer. If smaller
	 *      than DEFAULT_BUFFER_SIZE, then default is used
	 */
	public FileRecordBuffer(String tmpFilePath, int dataBufferSize) {
		this.tmpFilePath = tmpFilePath;
		readPosition = 0;
		writePosition = 0;
		mapPosition = 0;
		isDirty = false;
		hasFile = false;
		isClosed=false;
		dataBuffer = ByteBuffer.allocateDirect(dataBufferSize > DEFAULT_BUFFER_SIZE ? dataBufferSize : DEFAULT_BUFFER_SIZE);
	}


	/**
	 *  Constructor for the FileRecordBuffer object
	 *
	 *@param  tmpFilePath  Name of the subdirectory where to create TMP files or
	 *      NULL (the system default will be used)
	 */
	public FileRecordBuffer(String tmpFilePath) {
		this.tmpFilePath = tmpFilePath;
		readPosition = 0;
		writePosition = 0;
		mapPosition = 0;
		isDirty = false;
		hasFile = false;
		isClosed=false;
		dataBuffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
	}


	/**
	 *  Opens buffer, creates temporary file.
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	private void openTmpFile() throws IOException {
		if (tmpFilePath != null) {
			tmpFile = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX, new File(tmpFilePath));
		} else {
			tmpFile = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX);
		}
		tmpFile.deleteOnExit();
		// we want the temp file be deleted on exit
		tmpFileChannel = new RandomAccessFile(tmpFile, TMP_FILE_MODE).getChannel();
		hasFile=true;
	}


	/**
	 *  Closes buffer, removes temporary file (is exists)
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	public void close() throws IOException {
		isClosed=true;
		if (hasFile) {
			tmpFileChannel.close();
			if (!tmpFile.delete()) {
				throw new IOException("Can't delete TMP file: " + tmpFile.getAbsoluteFile());
			}
		}
		hasFile = false;
	}


	/**
	 *  Rewinds the buffer. Next shift operation returns first record stored.
	 *
	 *@since    September 19, 2002
	 */
	public void rewind() {
		readPosition = 0;
	}


	/**
	 *  Clears the buffer. Temp file (if it was created) remains
	 * unchanged size-wise
	 */
	public void clear() {
		readPosition = 0;
		writePosition = 0;
		mapPosition = 0;
		isDirty = false;
		dataBuffer.clear();
	}


	/**
	 *  Stores one data record into buffer.
	 *
	 *@param  data             ByteBuffer containing record's data
	 *@exception  IOException  In case of IO failure
	 *@since                   September 17, 2002
	 */
	public void push(ByteBuffer data) throws IOException {
		if(isClosed){
			throw new IOException("Buffer has been closed !");
		}
		
		int recordSize = data.remaining();

		secureBuffer(writePosition, recordSize + LEN_SIZE_SPECIFIER);
		try {
			dataBuffer.position(writePosition - mapPosition);
			dataBuffer.putInt(recordSize);
			dataBuffer.put(data);
			writePosition += (recordSize + LEN_SIZE_SPECIFIER);
			isDirty = true;
		} catch (BufferOverflowException ex) {
			throw new IOException("Input Buffer is not big enough to accomodate data record !");
		}
	}


	/**
	 *  Checks whether in memory buffer has to be reloaded/flushed
	 *
	 *@param  position       Description of the Parameter
	 *@param  requestedSize  Description of the Parameter
	 *@return                Description of the Return Value
	 */
	private final boolean needRemap(int position, int requestedSize) {
		if (position < mapPosition || position > (mapPosition + dataBuffer.remaining())) {
			return true;
		} else if (position - mapPosition + requestedSize > dataBuffer.remaining()) {
			return true;
		} else {
			return false;
		}
	}


	/**
	 *  Secures that in memory buffer is "mapped" from proper location and
	 *  populated with data from TMP file (is needed)
	 *
	 *@param  position         Description of the Parameter
	 *@param  requestedSize    Description of the Parameter
	 *@exception  IOException  Description of the Exception
	 */
	private final void secureBuffer(int position, int requestedSize) throws IOException {
		if (needRemap(position, requestedSize)) {
			flushBuffer();
			boolean reloadNeed = position < writePosition ? true : false;
			mapBuffer(position, reloadNeed);
		}
	}


	/**
	 *  Returns next record from the buffer - FIFO order.
	 *
	 *@param  data             ByteBuffer into which store data
	 *@return                  ByteBuffer populated with record's data or NULL if
	 *      no more record can be retrieved
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	public ByteBuffer shift(ByteBuffer data) throws IOException {
		int recordSize;
		if(isClosed){
			throw new IOException("Buffer has been closed !");
		}
		if (readPosition >= writePosition) {
			return null;
		}
		secureBuffer(readPosition, LEN_SIZE_SPECIFIER);
		dataBuffer.position(readPosition - mapPosition);
		recordSize = dataBuffer.getInt();
		readPosition += LEN_SIZE_SPECIFIER;
		secureBuffer(readPosition, recordSize);
		int oldLimit = dataBuffer.limit();
		dataBuffer.limit(dataBuffer.position() + recordSize);
		data.put(dataBuffer);
		dataBuffer.limit(oldLimit);
		readPosition += recordSize;
		return data;
	}


	/**
	 *  Reads next record from the buffer but leaves the record there - FIFO order.
	 *  Subsequent calls to this method returns the same record.	
	 *
	 *@param  data             ByteBuffer into which store data
	 *@return                  ByteBuffer populated with record's data or NULL if
	 *      no more record can be retrieved
	 *@exception  IOException  Description of the Exception
	 */
	public ByteBuffer get(ByteBuffer data) throws IOException {
		int recordSize;
		if(isClosed){
			throw new IOException("Buffer has been closed !");
		}
		if (readPosition >= writePosition) {
			return null;
		}
		secureBuffer(readPosition, LEN_SIZE_SPECIFIER);
		dataBuffer.mark();
		dataBuffer.position(readPosition - mapPosition);
		recordSize = dataBuffer.getInt();
		readPosition += LEN_SIZE_SPECIFIER;
		secureBuffer(readPosition, recordSize);
		int oldLimit = dataBuffer.limit();
		dataBuffer.limit(dataBuffer.position() + recordSize);
		data.put(dataBuffer);
		dataBuffer.limit(oldLimit);
		dataBuffer.reset();
		return data;
	}


	/**
	 *  Flushes in memory buffer into TMP file
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	private void flushBuffer() throws IOException {
		if (isDirty) {
			dataBuffer.flip();
			if (!hasFile) {
				openTmpFile();
			}
			tmpFileChannel.write(dataBuffer, mapPosition);
		}
		dataBuffer.clear();
		isDirty = false;
	}

	public boolean isEmpty(){
		return (readPosition>=writePosition ? true : false);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  fromPosition     Description of the Parameter
	 *@param  reload           Description of the Parameter
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	private void mapBuffer(int fromPosition, boolean reload) throws IOException {
		dataBuffer.clear();
		mapPosition = fromPosition;
		if (reload) {
			if (hasFile) {
				tmpFileChannel.read(dataBuffer, mapPosition);
				dataBuffer.flip();
			} else {
				throw new RuntimeException("Can't remap buffer TMP file doesn't exist");
			}
		}
		isDirty = false;
	}

//	public static void main(String argv[]) {
//		byte[] charArray = new byte[100];
//		String text;
//		int nRecords = 200;
//		ByteBuffer data = ByteBuffer.allocateDirect(80);
//		ByteBuffer outData;
//		FileRecordBuffer fBuffer;
//		System.out.println("Testing FileRecord Buffer");
//		fBuffer = new FileRecordBuffer(null);
//
//		System.out.println("Temp file location: " + fBuffer.tmpFile.getAbsolutePath());
//		System.out.println();
//		System.out.println("Adding testing records #" + nRecords);
//		for (int i = 0; i < 200; i++) {
//			data.clear();
//			text = new String("THIS IS TESTING RECORD no " + i);
//			data.put(text.getBytes());
//			data.putInt(i);
//			data.flip();
//			try {
//				fBuffer.push(data);
//			} catch (IOException ex) {
//				ex.printStackTrace();
//			}
//		}
//		System.out.println("Finished!");
//		System.out.println("Reading 100 Testing records");
//		for (int i = 0; i < 100; i++) {
//			data.clear();
//			outData = null;
//			try {
//				outData = fBuffer.shift(data);
//			} catch (IOException ex) {
//				ex.printStackTrace();
//			}
//			System.out.print("Rec no: " + i + " ");
//			if (data != null) {
//				outData.flip();
//				outData.get(charArray, 0, outData.remaining());
//				System.out.print(new String(charArray));
//			}
//			System.out.println();
//		}
//		System.out.println("Adding testing records #100");
//		for (int i = 200; i < 300; i++) {
//			data.clear();
//			text = new String("THIS IS TESTING RECORD no " + i);
//			data.put(text.getBytes());
//			data.putInt(i);
//			data.flip();
//			try {
//				fBuffer.push(data);
//			} catch (IOException ex) {
//				ex.printStackTrace();
//			}
//		}
//
//		System.out.println("Reading testing records #" + nRecords);
//		fBuffer.rewind();
//		do {
//			data.clear();
//			outData = null;
//			try {
//				outData = fBuffer.shift(data);
//			} catch (IOException ex) {
//				ex.printStackTrace();
//			}
//			System.out.print("Rec : ");
//			if (outData != null) {
//				outData.flip();
//				for (int i = 0; i < charArray.length; charArray[i++] = 0) {
//					;
//				}
//				outData.get(charArray, 0, outData.remaining());
//				System.out.print(new String(charArray));
//			}
//			System.out.println();
//		} while (outData != null);
//		try {
//			fBuffer.close();
//		} catch (IOException ex) {
//			ex.printStackTrace();
//		}
//
//	}
//
}

