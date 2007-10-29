package ca.neo.ui.actions;

import javax.swing.JDialog;

import ca.neo.math.Function;
import ca.neo.ui.configurable.ConfigException;
import ca.neo.ui.configurable.IConfigurable;
import ca.neo.ui.configurable.PropertyDescriptor;
import ca.neo.ui.configurable.PropertySet;
import ca.neo.ui.configurable.descriptors.PFloat;
import ca.neo.ui.configurable.managers.UserTemplateConfigurer;
import ca.neo.ui.util.DialogPlotter;
import ca.shu.ui.lib.actions.ActionException;
import ca.shu.ui.lib.actions.StandardAction;

/**
 * Plots a function node, which can contain multiple functions
 * 
 * @author Shu Wu
 */
public class PlotFunctionAction extends StandardAction implements IConfigurable {
	private static final long serialVersionUID = 1L;

	static final PropertyDescriptor pEnd = new PFloat("End");
	static final PropertyDescriptor pIncrement = new PFloat("Increment");
	static final PropertyDescriptor pStart = new PFloat("Start");
	// static final PropDescriptor pTitle = new PTString("Title");
	private Function function;

	private String plotName;

	private JDialog dialogParent;

	public PlotFunctionAction(String plotName, String actionName,
			Function function, JDialog dialogParent) {
		super("Plot function input", actionName);
		this.plotName = plotName;
		this.function = function;
		this.dialogParent = dialogParent;
	}

	@Override
	protected void action() throws ActionException {

		UserTemplateConfigurer config = new UserTemplateConfigurer(this);
		try {
			config.configureAndWait();
		} catch (ConfigException e) {
			e.defaultHandleBehavior();
		}

	}

	public void completeConfiguration(PropertySet properties)
			throws ConfigException {
		String title = plotName + " - Function Plot";

		float start = (Float) properties.getProperty(pStart);
		float end = (Float) properties.getProperty(pEnd);
		float increment = (Float) properties.getProperty(pIncrement);

		DialogPlotter plotter = new DialogPlotter(dialogParent);

		try {
			plotter.doPlot(function, start, increment, end, title + " ("
					+ function.getClass().getSimpleName() + ")");
		} catch (Exception e) {
			throw new ConfigException(e.getMessage());
		}

	}

	public PropertyDescriptor[] getConfigSchema() {

		PropertyDescriptor[] properties = { pStart, pIncrement, pEnd };
		return properties;

	}

	public String getTypeName() {
		return "Function Node plotter";
	}

}