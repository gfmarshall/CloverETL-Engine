/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.component.rollup;

import java.util.Properties;

import org.jetel.ctl.CTLAbstractTransform;
import org.jetel.ctl.CTLEntryPoint;
import org.jetel.data.DataRecord;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.TransformException;
import org.jetel.metadata.DataRecordMetadata;

/**
 * Base class of all Java transforms generated by CTL-to-Java compiler from CTL transforms in the Rollup component.
 *
 * @author Martin Janik, Javlin a.s. &lt;martin.janik@javlin.eu&gt;
 *
 * @version 5th May 2010
 * @created 22nd April 2010
 *
 * @see RecordRollup
 */
public abstract class CTLRecordRollup extends CTLAbstractTransform implements RecordRollup {

	public final void init(Properties parameters, DataRecordMetadata inputMetadata, DataRecordMetadata accumulatorMetadata,
			DataRecordMetadata[] outputMetadata) throws ComponentNotReadyException {
		// a single input data record is required, array of output data records is provided directly via method call
		this.inputRecords = new DataRecord[1];

		globalScopeInit();
		initDelegate();
	}

	/**
	 * Called by {@link #init(Properties, DataRecordMetadata, DataRecordMetadata, DataRecordMetadata[])} to perform
	 * user-specific initialization defined in the CTL transform. The default implementation does nothing, may be
	 * overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if the initialization fails
	 */
	@CTLEntryPoint(name = "init", required = false)
	protected void initDelegate() throws ComponentNotReadyException {
		// does nothing by default, may be overridden by generated transform classes
	}

	public final void initGroup(DataRecord inputRecord, DataRecord groupAccumulator) throws TransformException {
		this.inputRecords[0] = inputRecord;
		this.outputRecords = NO_DATA_RECORDS;

		try {
			initGroupDelegate(groupAccumulator);
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/**
	 * Called by {@link #initGroup(DataRecord, DataRecord)} to init processing of a group of data records in a
	 * user-specific way defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "initGroup", parameterNames = { "groupAccumulator" }, required = true)
	protected abstract void initGroupDelegate(DataRecord groupAccumulator)
			throws ComponentNotReadyException, TransformException;

	public final boolean updateGroup(DataRecord inputRecord, DataRecord groupAccumulator) throws TransformException {
		this.inputRecords[0] = inputRecord;
		this.outputRecords = NO_DATA_RECORDS;

		try {
			return updateGroupDelegate(groupAccumulator);
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.rollup.RecordRollup#preExecute()
	 */
	public void preExecute() throws ComponentNotReadyException {
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.component.rollup.RecordRollup#postExecute(org.jetel.graph.TransactionMethod)
	 */
	public void postExecute() throws ComponentNotReadyException {
	}
	
	/**
	 * Called by {@link #updateGroup(DataRecord, DataRecord)} to update processing of a group of data records in a
	 * user-specific way defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "updateGroup", parameterNames = { "groupAccumulator" }, required = true)
	protected abstract boolean updateGroupDelegate(DataRecord groupAccumulator)
			throws ComponentNotReadyException, TransformException;

	public final boolean finishGroup(DataRecord inputRecord, DataRecord groupAccumulator) throws TransformException {
		this.inputRecords[0] = inputRecord;
		this.outputRecords = NO_DATA_RECORDS;

		try {
			return finishGroupDelegate(groupAccumulator);
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/**
	 * Called by {@link #finishGroup(DataRecord, DataRecord)} to finish processing of a group of data records in a
	 * user-specific way defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "finishGroup", parameterNames = { "groupAccumulator" }, required = true)
	protected abstract boolean finishGroupDelegate(DataRecord groupAccumulator)
			throws ComponentNotReadyException, TransformException;

	public final int updateTransform(int counter, DataRecord inputRecord, DataRecord groupAccumulator,
			DataRecord[] outputRecords) throws TransformException {
		this.inputRecords[0] = inputRecord;
		this.outputRecords = outputRecords;

		try {
			return updateTransformDelegate(counter, groupAccumulator);
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/**
	 * Called by {@link #updateTransform(int, DataRecord, DataRecord, DataRecord[])} to transform data records in a
	 * user-specific way defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "updateTransform", parameterNames = { "counter", "groupAccumulator" }, required = true)
	protected abstract int updateTransformDelegate(int counter, DataRecord groupAccumulator)
			throws ComponentNotReadyException, TransformException;

	public final int transform(int counter, DataRecord inputRecord, DataRecord groupAccumulator,
			DataRecord[] outputRecords) throws TransformException {
		this.inputRecords[0] = inputRecord;
		this.outputRecords = outputRecords;

		try {
			return transformDelegate(counter, groupAccumulator);
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/**
	 * Called by {@link #transform(int, DataRecord, DataRecord, DataRecord[])} to transform data records in a
	 * user-specific way defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "transform", parameterNames = { "counter", "groupAccumulator" }, required = true)
	protected abstract int transformDelegate(int counter, DataRecord groupAccumulator)
			throws ComponentNotReadyException, TransformException;

	@CTLEntryPoint(name = "getMessage", required = false)
	public String getMessage() {
		// null by default, may be overridden by generated transform classes
		return null;
	}

	@CTLEntryPoint(name = "finished", required = false)
	public void finished() {
		// does nothing by default, may be overridden by generated transform classes
	}

	@CTLEntryPoint(name = "reset", required = false)
	public void reset() throws ComponentNotReadyException {
		// does nothing by default, may be overridden by generated transform classes
	}

}
