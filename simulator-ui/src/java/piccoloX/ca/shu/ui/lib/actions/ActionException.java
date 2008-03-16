package ca.shu.ui.lib.actions;

import ca.shu.ui.lib.exceptions.UIException;
import ca.shu.ui.lib.util.UserMessages;
import ca.shu.ui.lib.util.Util;

/**
 * Exception thrown during an action
 * 
 * @author Shu Wu
 */
public class ActionException extends UIException {
	private static final long serialVersionUID = 1L;

	/**
	 * Whether an warning should be shown when the action is handled by defaults
	 */
	private final boolean showWarning;

	public ActionException(Exception e) {
		this(e.getMessage(), true);
	}

	/**
	 * @param description
	 *            Description of the exception
	 */
	public ActionException(String description) {
		this(description, true);
	}

	/**
	 * @param description
	 *            Description of the exception
	 * @param showWarningPopup
	 *            If true, a warning should be shown to the user
	 */
	public ActionException(String description, boolean showWarningPopup) {
		super(description);

		this.showWarning = showWarningPopup;

	}

	@Override
	public void defaultHandleBehavior() {

		if (showWarning) {
			Util.debugMsg("Action Exception: " + toString());
			UserMessages.showWarning(getMessage());

		} else {
			Util.debugMsg("Action Exception: " + toString());
		}
	}

}
