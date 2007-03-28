/**
 * 
 */
package org.jetel.component.aggregate;

import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.metadata.DataFieldMetadata;

/**
 * Calculates the minimum value of a field.
 * 
 * Output field must be of the same type as the input field. If input field is nullable, then output field must
 * be nullable too.
 * 
 * @author Jaroslav Urban
 *
 */
public class Min extends AggregateFunction {
	private static final String NAME = "MIN";

	private DataField min;

	// Is input nullable?
	private boolean nullableInput;

	
	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#checkInputFieldType(org.jetel.metadata.DataFieldMetadata)
	 */
	@Override
	public boolean checkInputFieldType(DataFieldMetadata inputField) {
		nullableInput = inputField.isNullable();
		return true;
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#checkOutputFieldType(org.jetel.metadata.DataFieldMetadata)
	 */
	@Override
	public boolean checkOutputFieldType(DataFieldMetadata outputField) {
		if (nullableInput && !outputField.isNullable()) {
			return false;
		}
		return inputFieldMetadata.getType() == outputField.getType();
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#init()
	 */
	@Override
	public void init() {
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#requiresInputField()
	 */
	@Override
	public boolean requiresInputField() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#storeResult(org.jetel.data.DataField)
	 */
	@Override
	public void storeResult(DataField outputField) {
		if (min == null) {
			outputField.setNull(true);
			return;
		}
		outputField.copyFrom(min);
	}

	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#update(org.jetel.data.DataRecord)
	 */
	@Override
	public void update(DataRecord record) {
		DataField input = record.getField(inputFieldIndex);
		if (input.isNull()) {
			return;
		}
		
		if ((min == null) || (input.compareTo(min) == -1)) {
			min = input.duplicate();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.component.aggregate.AggregateFunction#getName()
	 */
	@Override
	public String getName() {
		return NAME;
	}
}
