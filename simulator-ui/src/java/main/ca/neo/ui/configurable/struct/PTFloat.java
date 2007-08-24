package ca.neo.ui.configurable.struct;

import ca.neo.ui.configurable.ConfigParamDescriptor;
import ca.neo.ui.configurable.ConfigParamInputPanel;

public class PTFloat extends ConfigParamDescriptor {

	private static final long serialVersionUID = 1L;

	public PTFloat(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public ConfigParamInputPanel createInputPanel() {
		// TODO Auto-generated method stub
		return new FloatPanel(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class getTypeClass() {
		/*
		 * Return the primitive type
		 */
		return float.class;
	}

	@Override
	public String getTypeName() {
		return "Float";
	}

}