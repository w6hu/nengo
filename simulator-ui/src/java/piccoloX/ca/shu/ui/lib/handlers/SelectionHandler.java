/*
 * Copyright (c) 2002-@year@, University of Maryland
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of the University of Maryland nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Piccolo was written at the Human-Computer Interaction Laboratory www.cs.umd.edu/hcil by Jesse Grosjean
 * under the supervision of Ben Bederson. The Piccolo website is www.cs.umd.edu/hcil/piccolo.
 */
package ca.shu.ui.lib.handlers;

import java.awt.BasicStroke;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ca.shu.ui.lib.actions.DragAction;
import ca.shu.ui.lib.objects.SelectionBorder;
import ca.shu.ui.lib.world.World;
import ca.shu.ui.lib.world.WorldGround;
import ca.shu.ui.lib.world.WorldObject;
import ca.shu.ui.lib.world.WorldSky;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PDragSequenceEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PDimension;
import edu.umd.cs.piccolo.util.PNodeFilter;
import edu.umd.cs.piccolox.event.PNotificationCenter;

/**
 * <code>PSelectionEventHandler</code> provides standard interaction for
 * selection. Clicking selects the object under the cursor. Shift-clicking
 * allows multiple objects to be selected. Dragging offers marquee selection.
 * Pressing the delete key deletes the selection by default.
 * 
 * @version 1.0
 * @author Ben Bederson, modified by Shu Wu
 */
public class SelectionHandler extends PDragSequenceEventHandler {

	public static final String SELECTION_CHANGED_NOTIFICATION = "SELECTION_CHANGED_NOTIFICATION";

	public static final String SELECTION_HANDLER_FRAME_ATTR = "SelHandlerFrame";
	final static int DASH_WIDTH = 5;

	final static int NUM_STROKES = 10;
	private HashMap<WorldObject, Boolean> allItems = null; // Used within drag
	private Point2D canvasPressPt = null;
	private boolean deleteKeyActive = true; // True if DELETE key should delete
	// children can be selected
	private PPath marquee = null;
	// temporarily
	private HashMap<WorldObject, Boolean> marqueeMap = null;
	// selection
	private Paint marqueePaint;
	private float marqueePaintTransparency = 1.0f;
	private WorldSky marqueeParent = null; // Node that marquee is added to as
	private Paint marqueeStrokePaint;
	private WorldObject pressNode = null; // Node pressed on (or null if none)
	// a
	// child
	private Point2D presspt = null;
	// selection
	private WorldGround selectableParent = null; // List of nodes whose
	private HashMap<WorldObject, Boolean> selection = null; // The current
	private float strokeNum = 0;
	private Stroke[] strokes = null;

	// handler temporarily
	private ArrayList<WorldObject> unselectList = null; // Used within drag
	// handler

	World world;

	/**
	 * Creates a selection event handler.
	 * 
	 * @param marqueeParent
	 *            The node to which the event handler dynamically adds a marquee
	 *            (temporarily) to represent the area being selected.
	 * @param selectableParent
	 *            The node whose children will be selected by this event
	 *            handler.
	 */
	public SelectionHandler(World world) {
		this.world = world;
		this.marqueeParent = world.getSky();
		this.selectableParent = world.getGround();
		// setEventFilter(new PInputEventFilter(InputEvent.BUTTON1_MASK));
		init();
	}

	// /////////////////////////////////////////////////////
	// Public static methods for manipulating the selection
	// /////////////////////////////////////////////////////

	private boolean internalSelect(WorldObject node) {
		if (isSelected(node)) {
			return false;
		}

		selection.put(node, Boolean.TRUE);
		decorateSelectedNode(node);
		return true;
	}

	private boolean internalUnselect(WorldObject node) {
		if (!isSelected(node)) {
			return false;
		}

		undecorateSelectedNode(node);
		selection.remove(node);
		return true;
	}

	private void postSelectionChanged() {
		PNotificationCenter.defaultCenter().postNotification(
				SELECTION_CHANGED_NOTIFICATION, this);
	}

	protected void computeMarqueeSelection(PInputEvent pie) {
		unselectList.clear();
		// Make just the items in the list selected
		// Do this efficiently by first unselecting things not in the list
		Iterator<WorldObject> selectionEn = selection.keySet().iterator();
		while (selectionEn.hasNext()) {
			WorldObject node = selectionEn.next();
			if (!allItems.containsKey(node)) {
				unselectList.add(node);
			}
		}
		unselect(unselectList);

		// Then select the rest
		selectionEn = allItems.keySet().iterator();
		while (selectionEn.hasNext()) {
			WorldObject node = selectionEn.next();
			if (!selection.containsKey(node) && !marqueeMap.containsKey(node)
					&& isSelectable(node)) {
				marqueeMap.put(node, Boolean.TRUE);
			} else if (!isSelectable(node)) {
				selectionEn.remove();
			}
		}

		select(allItems);
	}

	protected void computeOptionMarqueeSelection(PInputEvent pie) {
		unselectList.clear();
		Iterator<WorldObject> selectionEn = selection.keySet().iterator();
		while (selectionEn.hasNext()) {
			WorldObject node = selectionEn.next();
			if (!allItems.containsKey(node) && marqueeMap.containsKey(node)) {
				marqueeMap.remove(node);
				unselectList.add(node);
			}
		}
		unselect(unselectList);

		// Then select the rest
		selectionEn = allItems.keySet().iterator();
		while (selectionEn.hasNext()) {
			WorldObject node = selectionEn.next();
			if (!selection.containsKey(node) && !marqueeMap.containsKey(node)
					&& isSelectable(node)) {
				marqueeMap.put(node, Boolean.TRUE);
			} else if (!isSelectable(node)) {
				selectionEn.remove();
			}
		}

		select(allItems);
	}

	protected PNodeFilter createNodeFilter(PBounds bounds) {
		return new BoundsFilter(bounds);
	}

	protected void drag(PInputEvent e) {
		super.drag(e);

		if (isMarqueeSelection(e)) {
			updateMarquee(e);

			if (!isOptionSelection(e)) {
				computeMarqueeSelection(e);
			} else {
				computeOptionMarqueeSelection(e);
			}
		} else {
			dragStandardSelection(e);

		}
	}

	/**
	 * This gets called continuously during the drag, and is used to animate the
	 * marquee
	 */
	protected void dragActivityStep(PInputEvent aEvent) {
		if (marquee != null) {
			float origStrokeNum = strokeNum;
			strokeNum = (strokeNum + 0.5f) % NUM_STROKES; // Increment by
			// partial steps to
			// slow down
			// animation
			if ((int) strokeNum != (int) origStrokeNum) {
				marquee.setStroke(strokes[(int) strokeNum]);
			}
		}
	}

	protected void dragStandardSelection(PInputEvent e) {

		PDimension gDist = new PDimension();
		Iterator<WorldObject> selectionEn = getSelection().iterator();

		if (selectionEn.hasNext()) {
			e.setHandled(true);
			PDimension d = e.getDeltaRelativeTo(selectableParent);

			while (selectionEn.hasNext()) {
				WorldObject node = selectionEn.next();

				gDist.setSize(d);
				node.getParent().globalToLocal(gDist);
				node.offset(gDist.getWidth(), gDist.getHeight());
			}

		}

	}

	protected void endDrag(PInputEvent e) {
		super.endDrag(e);

		if (isMarqueeSelection(e)) {
			endMarqueeSelection(e);
		} else {
			dragAction.setFinalPositions();
			dragAction.doAction();
			dragAction = null;

			if (getSelection().size() == 1) {
				unselectAll();
			}
			endStandardSelection(e);
		}
	}

	protected void endMarqueeSelection(PInputEvent e) {
		// Remove marquee
		marquee.removeFromParent();
		marquee = null;
	}

	protected void endStandardSelection(PInputEvent e) {
		pressNode = null;
	}

	protected PBounds getMarqueeBounds() {
		if (marquee != null) {
			return marquee.getBounds();
		}
		return new PBounds();
	}

	protected void init() {
		float[] dash = { DASH_WIDTH, DASH_WIDTH };
		strokes = new Stroke[NUM_STROKES];
		for (int i = 0; i < NUM_STROKES; i++) {
			strokes[i] = new BasicStroke(1, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_MITER, 1, dash, i);
		}

		selection = new HashMap<WorldObject, Boolean>();
		allItems = new HashMap<WorldObject, Boolean>();
		unselectList = new ArrayList<WorldObject>();
		marqueeMap = new HashMap<WorldObject, Boolean>();
	}

	protected void initializeMarquee(PInputEvent e) {

		marquee = PPath.createRectangle((float) presspt.getX(), (float) presspt
				.getY(), 0, 0);
		marquee.setPaint(marqueePaint);
		marquee.setTransparency(marqueePaintTransparency);
		marquee.setStrokePaint(marqueeStrokePaint);
		marquee.setStroke(strokes[0]);

		marqueeParent.addChild(marquee);

		marqueeMap.clear();
	}

	protected void initializeSelection(PInputEvent pie) {
		canvasPressPt = pie.getCanvasPosition();
		presspt = pie.getPosition();

		PNode node = pie.getPath().getPickedNode();

		while (node != null) {
			if (node == marqueeParent) {
				pressNode = null;
				return;
			}

			if (node == selectableParent) {
				pressNode = null;
				return;
			}

			if ((node instanceof WorldObject)
					&& ((WorldObject) node).isSelectable()) {
				pressNode = (WorldObject) node;
				pressNode.moveToFront();
				return;
			}

			node = node.getParent();
		}

	}

	// //////////////////////////////////////////////////////
	// The overridden methods from PDragSequenceEventHandler
	// //////////////////////////////////////////////////////

	protected boolean isMarqueeSelection(PInputEvent pie) {
		return (pressNode == null && world.isSelectionMode());
	}

	/**
	 * Determine if the specified node is selectable (i.e., if it is a child of
	 * the one the list of selectable parents.
	 */
	protected boolean isSelectable(PNode node) {
		boolean selectable = false;

		if (node != null && selectableParent.isAncestorOf(node)) {
			selectable = true;

		}

		return selectable;
	}

	DragAction dragAction;

	protected void startDrag(PInputEvent e) {
		super.startDrag(e);

		initializeSelection(e);

		if (isMarqueeSelection(e)) {
			initializeMarquee(e);

			if (!isOptionSelection(e)) {
				startMarqueeSelection(e);
			} else {
				startOptionMarqueeSelection(e);
			}
		} else {
			if (!isOptionSelection(e)) {
				startStandardSelection(e);
			} else {
				startStandardOptionSelection(e);
			}

			Collection<WorldObject> nodes = getSelection();
			dragAction = new DragAction(nodes);

		}
	}

	// //////////////////////////
	// Additional methods
	// //////////////////////////

	protected void startMarqueeSelection(PInputEvent e) {
		unselectAll();
	}

	protected void startOptionMarqueeSelection(PInputEvent e) {
	}

	protected void startStandardOptionSelection(PInputEvent pie) {
		// Option indicator is down, toggle selection
		if (isSelectable(pressNode)) {
			if (isSelected(pressNode)) {
				unselect(pressNode);
			} else {
				select(pressNode);
			}
		}
	}

	protected void startStandardSelection(PInputEvent pie) {
		// Option indicator not down - clear selection, and start fresh
		if (!isSelected(pressNode)) {
			unselectAll();

			if (isSelectable(pressNode)) {
				select(pressNode);
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void updateMarquee(PInputEvent pie) {
		PBounds b = new PBounds();

		if (marqueeParent instanceof PCamera) {
			b.add(canvasPressPt);
			b.add(pie.getCanvasPosition());
		} else {
			b.add(presspt);
			b.add(pie.getPosition());
		}

		b.reset();
		b.add(presspt);
		b.add(pie.getPosition());

		PBounds marqueeBounds = (PBounds) b.clone();

		selectableParent.globalToLocal(marqueeBounds);

		marqueeParent.viewToLocal(marqueeBounds);

		// marquee.globalToLocal(b);
		marquee.setPathToRectangle((float) marqueeBounds.x,
				(float) marqueeBounds.y, (float) marqueeBounds.width,
				(float) marqueeBounds.height);

		allItems.clear();
		PNodeFilter filter = createNodeFilter(b);

		Collection<PNode> items;

		items = selectableParent.getAllNodes(filter, null);

		Iterator<PNode> itemsIt = items.iterator();
		while (itemsIt.hasNext()) {
			allItems.put((WorldObject) itemsIt.next(), Boolean.TRUE);
		}

	}

	public void decorateSelectedNode(WorldObject node) {
		SelectionBorder frame = new SelectionBorder(world, node);
		// frame.setFrameColor(Style.COLOR_BORDER_DRAGGED);

		node.addAttribute(SELECTION_HANDLER_FRAME_ATTR, frame);

		// PBoundsHandle.addBoundsHandlesTo(node);
	}

	/**
	 * Indicates the color used to paint the marquee.
	 * 
	 * @return the paint for interior of the marquee
	 */
	public Paint getMarqueePaint() {
		return marqueePaint;
	}

	/**
	 * Indicates the transparency level for the interior of the marquee.
	 * 
	 * @return Returns the marquee paint transparency, zero to one
	 */
	public float getMarqueePaintTransparency() {
		return marqueePaintTransparency;
	}

	/**
	 * Returns a copy of the currently selected nodes.
	 */
	public Collection<WorldObject> getSelection() {
		ArrayList<WorldObject> sel = new ArrayList<WorldObject>(selection
				.keySet());
		return sel;
	}

	/**
	 * Gets a reference to the currently selected nodes. You should not modify
	 * or store this collection.
	 */
	public Collection<WorldObject> getSelectionReference() {
		return Collections.unmodifiableCollection(selection.keySet());
	}

	public boolean getSupportDeleteKey() {
		return deleteKeyActive;
	}

	public boolean isDeleteKeyActive() {
		return deleteKeyActive;
	}

	public boolean isOptionSelection(PInputEvent pie) {
		return pie.isShiftDown();
	}

	public boolean isSelected(WorldObject node) {
		if ((node != null) && (selection.containsKey(node))) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Delete selection when delete key is pressed (if enabled)
	 */
	public void keyPressed(PInputEvent e) {
		switch (e.getKeyCode()) {
		case KeyEvent.VK_DELETE:
			if (deleteKeyActive) {
				Iterator<WorldObject> selectionEn = selection.keySet()
						.iterator();
				while (selectionEn.hasNext()) {
					WorldObject node = selectionEn.next();
					node.removeFromParent();
				}
				selection.clear();
			}
		}
	}

	public void select(Collection<WorldObject> items) {
		boolean changes = false;
		Iterator<WorldObject> itemIt = items.iterator();
		while (itemIt.hasNext()) {
			WorldObject node = itemIt.next();
			changes |= internalSelect(node);
		}
		if (changes) {
			postSelectionChanged();
		}
	}

	public void select(Map<WorldObject, Boolean> items) {
		select(items.keySet());
	}

	public void select(WorldObject node) {
		if (internalSelect(node)) {
			postSelectionChanged();
		}
	}

	/**
	 * Specifies if the DELETE key should delete the selection
	 */
	public void setDeleteKeyActive(boolean deleteKeyActive) {
		this.deleteKeyActive = deleteKeyActive;
	}

	/**
	 * Sets the color used to paint the marquee.
	 * 
	 * @param paint
	 *            the paint color
	 */
	public void setMarqueePaint(Paint paint) {
		this.marqueePaint = paint;
	}

	/**
	 * Sets the transparency level for the interior of the marquee.
	 * 
	 * @param marqueePaintTransparency
	 *            The marquee paint transparency to set.
	 */
	public void setMarqueePaintTransparency(float marqueePaintTransparency) {
		this.marqueePaintTransparency = marqueePaintTransparency;
	}

	// ////////////////////
	// Inner classes
	// ////////////////////

	public void setMarqueeStrokePaint(Paint marqueeStrokePaint) {
		this.marqueeStrokePaint = marqueeStrokePaint;
	}

	public void undecorateSelectedNode(WorldObject node) {

		Object frame = node.getAttribute(SELECTION_HANDLER_FRAME_ATTR);
		if (frame != null && frame instanceof SelectionBorder) {
			((SelectionBorder) frame).destroy();

		}
		node.addAttribute(SELECTION_HANDLER_FRAME_ATTR, null);

		// PBoundsHandle.removeBoundsHandlesFrom(node);
	}

	public void unselect(Collection<WorldObject> items) {
		boolean changes = false;
		Iterator<WorldObject> itemIt = items.iterator();
		while (itemIt.hasNext()) {
			WorldObject node = (WorldObject) itemIt.next();
			changes |= internalUnselect(node);
		}
		if (changes) {
			postSelectionChanged();
		}
	}

	public void unselect(WorldObject node) {
		if (internalUnselect(node)) {
			postSelectionChanged();
		}
	}

	public void unselectAll() {
		// Because unselect() removes from selection, we need to
		// take a copy of it first so it isn't changed while we're iterating
		ArrayList<WorldObject> sel = new ArrayList<WorldObject>(selection
				.keySet());
		unselect(sel);
	}

	protected class BoundsFilter implements PNodeFilter {
		PBounds bounds;
		PBounds localBounds = new PBounds();

		protected BoundsFilter(PBounds bounds) {
			this.bounds = bounds;
		}

		public boolean accept(PNode node) {
			localBounds.setRect(bounds);
			node.globalToLocal(localBounds);

			boolean boundsIntersects = node.intersects(localBounds);
			boolean isMarquee = (node == marquee);

			if (node instanceof WorldObject
					&& ((WorldObject) node).isSelectable()) {
				return (node.getPickable() && boundsIntersects && !isMarquee && !(node == selectableParent));
			} else {
				return false;
			}

		}

		public boolean acceptChildrenOf(PNode node) {
			return node == selectableParent;
		}

	}

}