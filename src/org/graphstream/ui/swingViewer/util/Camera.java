/*
 * Copyright 2006 - 2011 
 *     Julien Baudry	<julien.baudry@graphstream-project.org>
 *     Antoine Dutot	<antoine.dutot@graphstream-project.org>
 *     Yoann Pigné		<yoann.pigne@graphstream-project.org>
 *     Guilhelm Savin	<guilhelm.savin@graphstream-project.org>
 * 
 * This file is part of GraphStream <http://graphstream-project.org>.
 * 
 * GraphStream is a library whose purpose is to handle static or dynamic
 * graph, create them from scratch, file or any source and display them.
 * 
 * This program is free software distributed under the terms of two licenses, the
 * CeCILL-C license that fits European law, and the GNU Lesser General Public
 * License. You can  use, modify and/ or redistribute the software under the terms
 * of the CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
 * URL <http://www.cecill.info> or under the terms of the GNU LGPL as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C and LGPL licenses and that you accept their terms.
 */
package org.graphstream.ui.swingViewer.util;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;

import org.graphstream.graph.Node;
import org.graphstream.ui.geom.Point2;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.geom.Vector2;
import org.graphstream.ui.graphicGraph.GraphicEdge;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.graphicGraph.GraphicNode;
import org.graphstream.ui.graphicGraph.GraphicSprite;
import org.graphstream.ui.graphicGraph.stylesheet.Style;
import org.graphstream.ui.graphicGraph.stylesheet.Values;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants.Units;

/**
 * Define how the graph is viewed.
 * 
 * <p>
 * The camera is in charge of projecting the graph spaces in graph units (GU)
 * into user spaces (often in pixels). It defines the transformation (an affine
 * matrix) to passe from the first to the second. It also contains the graph
 * metrics, a set of values that give the overall dimensions of the graph in
 * graph units, as well as the view port, the area on the screen (or any
 * rendering surface) that will receive the results in pixels (or rendering
 * units).
 * </p>
 * 
 * <p>
 * The camera defines a centre at which it always points. It can zoom on the
 * graph, pan in any direction and rotate along two axes.
 * </p>
 * 
 * <p>
 * Knowing the transformation also allows to provide services like "what element
 * is not invisible ?" (not in the camera view) or "on what element is the mouse
 * cursor actually ?".
 * </p>
 */
public class Camera {
	// Attribute

	/**
	 * Information on the graph overall dimension and position.
	 */
	protected GraphMetrics metrics = new GraphMetrics();

	/**
	 * Automatic centring of the view.
	 */
	protected boolean autoFit = true;

	/**
	 * The camera centre of view.
	 */
	protected Point3 center = new Point3();

	/**
	 * The camera zoom.
	 */
	protected double zoom;

	/**
	 * The graph-space -> pixel-space transformation.
	 */
	protected AffineTransform Tx = new AffineTransform();

	/**
	 * The inverse transform of Tx.
	 */
	protected AffineTransform xT;

	/**
	 * The previous affine transform.
	 */
	protected AffineTransform oldTx;

	/**
	 * The rotation angle.
	 */
	protected double rotation;

	/**
	 * Padding around the graph.
	 */
	protected Values padding = new Values(Style.Units.GU, 0, 0, 0);

	/**
	 * Which node is visible. This allows to mark invisible nodes to fasten
	 * visibility tests for nodes, attached sprites and edges.
	 */
	protected HashSet<String> nodeInvisible = new HashSet<String>();

	/**
	 * The graph view port, if any. The graph view port is a view inside the
	 * graph space. It allows to compute the view according to a specified area
	 * of the graph space instead of the graph dimensions.
	 */
	protected double gviewport[] = null;
	
	// Construction

	/**
	 * New camera.
	 */
	public Camera() {
	}

	// Access

	/**
	 * The view centre (a point in graph units).
	 * 
	 * @return The view centre.
	 */
	public Point3 getViewCenter() {
		return center;
	}

	/**
	 * The visible portion of the graph.
	 * 
	 * @return A real for which value 1 means the graph is fully visible and
	 *         uses the whole view port.
	 */
	public double getViewPercent() {
		return zoom;
	}

	/**
	 * The rotation angle in degrees.
	 * 
	 * @return The rotation angle in degrees.
	 */
	public double getViewRotation() {
		return rotation;
	}

	/**
	 * Various sizes about the graph.
	 * 
	 * @return The graph metrics.
	 */
	public GraphMetrics getMetrics() {
		return metrics;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(String.format("Camera :%n"));

		builder.append(String.format("    autoFit  = %b%n", autoFit));
		builder.append(String.format("    center   = %s%n", center));
		builder.append(String.format("    rotation = %f%n", rotation));
		builder.append(String.format("    zoom     = %f%n", zoom));
		builder.append(String.format("    padding  = %s%n", padding));
		builder.append(String.format("    metrics  = %s%n", metrics));

		return builder.toString();
	}

	/**
	 * True if the element would be visible on screen. The method used is to
	 * transform the centre of the element (which is always in graph units)
	 * using the camera actual transformation to put it in pixel units. Then to
	 * look in the style sheet the size of the element and to test if its
	 * enclosing rectangle intersects the view port. For edges, its two nodes
	 * are used.
	 * 
	 * @param element
	 *            The element to test.
	 * @return True if the element is visible and therefore must be rendered.
	 */
	public boolean isVisible(GraphicElement element) {
		switch (element.getSelectorType()) {
		case NODE:
			return !nodeInvisible.contains(element.getId());
		case EDGE:
			return isEdgeVisible((GraphicEdge) element);
		case SPRITE:
			return isSpriteVisible((GraphicSprite) element);
		default:
			return false;
		}
	}

	/**
	 * Return the given point in pixels converted in graph units (GU) using the
	 * inverse transformation of the current projection matrix. The inverse
	 * matrix is computed only once each time a new projection matrix is
	 * created.
	 * 
	 * @param x
	 *            The source point abscissa in pixels.
	 * @param y
	 *            The source point ordinate in pixels.
	 * @return The resulting points in graph units.
	 */
	public Point2D.Double inverseTransform(double x, double y) {
		Point2D.Double p = new Point2D.Double(x, y);

		xT.transform(p, p);

		return p;
	}

	/**
	 * Transform a point in graph units into pixels.
	 * 
	 * @return The transformed point.
	 */
	public Point2D.Double transform(double x, double y) {
		Point2D.Double p = new Point2D.Double(x, y);

		Tx.transform(p, p);

		return p;
	}

	/**
	 * Process each node to check if it is in the actual view port, and mark
	 * invisible nodes. This method allows for fast node, sprite and edge
	 * visibility checking when drawing. This must be called before each
	 * rendering (if the view port changed).
	 */
	public void checkVisibility(GraphicGraph graph) {
		double W = metrics.viewport.data[0];
		double H = metrics.viewport.data[1];

		nodeInvisible.clear();

		for (Node node : graph) {
			boolean visible = isNodeIn((GraphicNode) node, 0, 0, W, H) && (! ((GraphicNode)node).hidden) && ((GraphicNode)node).positionned;

			if (!visible)
				nodeInvisible.add(node.getId());
		}
	}

	/**
	 * Search for the first node or sprite (in that order) that contains the
	 * point at coordinates (x, y).
	 * 
	 * @param graph
	 *            The graph to search for.
	 * @param x
	 *            The point abscissa.
	 * @param y
	 *            The point ordinate.
	 * @return The first node or sprite at the given coordinates or null if
	 *         nothing found.
	 */
	public GraphicElement findNodeOrSpriteAt(GraphicGraph graph, double x,
			double y) {
		for (Node n : graph) {
			GraphicNode node = (GraphicNode) n;

			if (nodeContains(node, x, y))
				return node;
		}

		for (GraphicSprite sprite : graph.spriteSet()) {
			if (spriteContains(sprite, x, y))
				return sprite;
		}

		return null;
	}

	/**
	 * Search for all the nodes and sprites contained inside the rectangle
	 * (x1,y1)-(x2,y2).
	 * 
	 * @param graph
	 *            The graph to search for.
	 * @param x1
	 *            The rectangle lowest point abscissa.
	 * @param y1
	 *            The rectangle lowest point ordinate.
	 * @param x2
	 *            The rectangle highest point abscissa.
	 * @param y2
	 *            The rectangle highest point ordinate.
	 * @return The set of sprites and nodes in the given rectangle.
	 */
	public ArrayList<GraphicElement> allNodesOrSpritesIn(GraphicGraph graph,
			double x1, double y1, double x2, double y2) {
		ArrayList<GraphicElement> elts = new ArrayList<GraphicElement>();

		for (Node node : graph) {
			if (isNodeIn((GraphicNode) node, x1, y1, x2, y2))
				elts.add((GraphicNode) node);
		}

		for (GraphicSprite sprite : graph.spriteSet()) {
			if (isSpriteIn(sprite, x1, y1, x2, y2))
				elts.add(sprite);
		}

		return elts;
	}

	/**
	 * Compute the real position of a sprite according to its eventual
	 * attachment in graph units.
	 * 
	 * @param sprite
	 *            The sprite.
	 * @param pos
	 *            Receiver for the sprite 2D position, can be null.
	 * @param units
	 *            The units in which the position must be computed (the sprite
	 *            already contains units).
	 * @return The same instance as the one given by parameter pos or a new one
	 *         if pos was null, containing the computed position in the given
	 *         units.
	 */
	public Point2D.Double getSpritePosition(GraphicSprite sprite,
			Point2D.Double pos, Units units) {
		if (sprite.isAttachedToNode())
			return getSpritePositionNode(sprite, pos, units);
		else if (sprite.isAttachedToEdge())
			return getSpritePositionEdge(sprite, pos, units);
		else
			return getSpritePositionFree(sprite, pos, units);
	}

	public double[] getGraphViewport() {
		return gviewport;
	}

	// Command

	public void setGraphViewport(double minx, double miny, double maxx, double maxy) {
		gviewport = new double[4];
		gviewport[0] = minx;
		gviewport[1] = miny;
		gviewport[2] = maxx;
		gviewport[3] = maxy;
	}

	public void removeGraphViewport() {
		gviewport = null;
	}

	/**
	 * Set the camera view in the given graphics and backup the previous
	 * transform of the graphics. Call {@link #popView(Graphics2D)} to restore
	 * the saved transform. You can only push one time the view.
	 * 
	 * @param g2
	 *            The Swing graphics to change.
	 */
	public void pushView(GraphicGraph graph, Graphics2D g2) {
		if (oldTx == null) {
			oldTx = g2.getTransform();

			if (autoFit)
				Tx = autoFitView(g2, Tx);
			else
				Tx = userView(g2, Tx);

			g2.setTransform(Tx);
		}
		
		checkVisibility(graph);
	}

	/**
	 * Restore the transform that was used before {@link #pushView(GraphicGraph, Graphics2D)}
	 * is used.
	 * 
	 * @param g2
	 *            The Swing graphics to restore.
	 */
	public void popView(Graphics2D g2) {
		if (oldTx != null) {
			g2.setTransform(oldTx);
			oldTx = null;
		}
	}

	/**
	 * Compute a transformation matrix that pass from graph units (user space)
	 * to pixel units (device space) so that the whole graph is visible.
	 * 
	 * @param g2
	 *            The Swing graphics.
	 * @param Tx
	 *            The transformation to modify.
	 * @return The transformation modified.
	 */
	protected AffineTransform autoFitView(Graphics2D g2, AffineTransform Tx) {
		double sx, sy;
		double tx, ty;
		double padXgu = getPaddingXgu() * 2;
		double padYgu = getPaddingYgu() * 2;
		double padXpx = getPaddingXpx() * 2;
		double padYpx = getPaddingYpx() * 2;

		sx = (metrics.viewport.data[0] - padXpx)
				/ (metrics.size.data[0] + padXgu); // Ratio along X
		sy = (metrics.viewport.data[1] - padYpx)
				/ (metrics.size.data[1] + padYgu); // Ratio along Y
		tx = metrics.lo.x + (metrics.size.data[0] / 2); // Centre of graph in X
		ty = metrics.lo.y + (metrics.size.data[1] / 2); // Centre of graph in Y

		if (sx > sy) // The least ratio.
			sx = sy;
		else
			sy = sx;

		Tx.setToIdentity();
		Tx.translate(metrics.viewport.data[0] / 2, metrics.viewport.data[1] / 2);
		if (rotation != 0)
			Tx.rotate(rotation / (180 / Math.PI));
		Tx.scale(sx, -sy);
		Tx.translate(-tx, -ty);

		xT = new AffineTransform(Tx);
		try {
			xT.invert();
		} catch (NoninvertibleTransformException e) {
			System.err.printf("cannot inverse gu2px matrix...%n");
		}

		zoom = 1;

		center.set(tx, ty, 0);
		metrics.setRatioPx2Gu(sx);
		metrics.loVisible.copy(metrics.lo);
		metrics.hiVisible.copy(metrics.hi);

		return Tx;
	}

	/**
	 * Compute a transformation that pass from graph units (user space) to a
	 * pixel units (device space) so that the view (zoom and centre) requested
	 * by the user is produced.
	 * 
	 * @param g2
	 *            The Swing graphics.
	 * @param Tx
	 *            The transformation to modify.
	 * @return The transformation modified.
	 */
	protected AffineTransform userView(Graphics2D g2, AffineTransform Tx) {
		double sx, sy;
		double tx, ty;
		double padXgu = getPaddingXgu() * 2;
		double padYgu = getPaddingYgu() * 2;
		double padXpx = getPaddingXpx() * 2;
		double padYpx = getPaddingYpx() * 2;
		double gw = gviewport != null ? gviewport[2] - gviewport[0]
				: metrics.size.data[0];
		double gh = gviewport != null ? gviewport[3] - gviewport[1]
				: metrics.size.data[1];
		// double diag = ((double)Math.max( metrics.size.data[0]+padXgu,
		// metrics.size.data[1]+padYgu )) * zoom;
		//
		// sx = ( metrics.viewport.data[0] - padXpx ) / diag;
		// sy = ( metrics.viewport.data[1] - padYpx ) / diag;
		sx = (metrics.viewport.data[0] - padXpx) / ((gw + padXgu) * zoom);
		sy = (metrics.viewport.data[1] - padYpx) / ((gh + padYgu) * zoom);
		tx = center.x;
		ty = center.y;

		if (sx > sy) // The least ratio.
			sx = sy;
		else
			sy = sx;

		Tx.setToIdentity();
		Tx.translate(metrics.viewport.data[0] / 2, metrics.viewport.data[1] / 2); 
		if (rotation != 0)
			Tx.rotate(rotation / (180 / Math.PI));
		Tx.scale(sx, -sy);
		Tx.translate(-tx, -ty);

		xT = new AffineTransform(Tx);
		try {
			xT.invert();
		} catch (NoninvertibleTransformException e) {
			System.err.printf("cannot inverse gu2px matrix...%n");
		}

		metrics.setRatioPx2Gu(sx);

		double w2 = (metrics.viewport.data[0] / sx) / 2;
		double h2 = (metrics.viewport.data[1] / sx) / 2;

		metrics.loVisible.set(center.x - w2, center.y - h2);
		metrics.hiVisible.set(center.x + w2, center.y + h2);

		return Tx;
	}

	/**
	 * Enable or disable automatic adjustment of the view to see the entire
	 * graph.
	 * 
	 * @param on
	 *            If true, automatic adjustment is enabled.
	 */
	public void setAutoFitView(boolean on) {
		if (autoFit && (!on)) {
			// We go from autoFit to user view, ensure the current centre is at
			// the
			// middle of the graph, and the zoom is at one.

			zoom = 1;
			center.set(metrics.lo.x + (metrics.size.data[0] / 2), metrics.lo.y
					+ (metrics.size.data[1] / 2), 0);
		}

		autoFit = on;
	}

	/**
	 * Set the centre of the view (the looked at point). As the viewer is only
	 * 2D, the z value is not required.
	 * 
	 * @param x
	 *            The new position abscissa.
	 * @param y
	 *            The new position ordinate.
	 */
	public void setCenter(double x, double y) {
		center.set(x, y, 0);
	}

	/**
	 * Set the zoom (or percent of the graph visible), 1 means the graph is
	 * fully visible.
	 * 
	 * @param z
	 *            The zoom.
	 */
	public void setZoom(double z) {
		zoom = z;
	}

	/**
	 * Set the rotation angle around the centre.
	 * 
	 * @param angle
	 *            The rotation angle in degrees.
	 */
	public void setRotation(double angle) {
		rotation = angle;
	}

	/**
	 * Set the output view port size in pixels.
	 * 
	 * @param viewportWidth
	 *            The width in pixels of the view port.
	 * @param viewportHeight
	 *            The width in pixels of the view port.
	 */
	public void setViewport(double viewportWidth, double viewportHeight) {
		metrics.setViewport(viewportWidth, viewportHeight);
	}

	/**
	 * Set the graph padding.
	 * 
	 * @param graph
	 *            The graphic graph.
	 */
	public void setPadding(GraphicGraph graph) {
		padding.copy(graph.getStyle().getPadding());
	}

	// Utility

	protected double getPaddingXgu() {
		if (padding.units == Style.Units.GU && padding.size() > 0)
			return padding.get(0);

		return 0;
	}

	protected double getPaddingYgu() {
		if (padding.units == Style.Units.GU && padding.size() > 1)
			return padding.get(1);

		return getPaddingXgu();
	}

	protected double getPaddingXpx() {
		if (padding.units == Style.Units.PX && padding.size() > 0)
			return padding.get(0);

		return 0;
	}

	protected double getPaddingYpx() {
		if (padding.units == Style.Units.PX && padding.size() > 1)
			return padding.get(1);

		return getPaddingXpx();
	}

	/**
	 * Check if a sprite is visible in the current view port.
	 * 
	 * @param sprite
	 *            The sprite to check.
	 * @return True if visible.
	 */
	protected boolean isSpriteVisible(GraphicSprite sprite) {
		return isSpriteIn(sprite, 0, 0, metrics.viewport.data[0],
				metrics.viewport.data[1]);
	}

	/**
	 * Check if an edge is visible in the current view port.
	 * 
	 * @param edge
	 *            The edge to check.
	 * @return True if visible.
	 */
	protected boolean isEdgeVisible(GraphicEdge edge) {
		GraphicNode node0 = edge.getNode0();
		GraphicNode node1 = edge.getNode1();
		
		if((!node1.positionned) || (!node0.positionned))
			return false;
		
		boolean node0Invis = nodeInvisible.contains(node0.getId());
		boolean node1Invis = nodeInvisible.contains(node1.getId());

		return !(node0Invis && node1Invis);
	}

	/**
	 * Is the given node visible in the given area.
	 * 
	 * @param node
	 *            The node to check.
	 * @param X1
	 *            The min abscissa of the area.
	 * @param Y1
	 *            The min ordinate of the area.
	 * @param X2
	 *            The max abscissa of the area.
	 * @param Y2
	 *            The max ordinate of the area.
	 * @return True if the node lies in the given area.
	 */
	protected boolean isNodeIn(GraphicNode node, double X1, double Y1, double X2,
			double Y2) {
		Values size = node.getStyle().getSize();
		double w2 = metrics.lengthToPx(size, 0) / 2;
		double h2 = size.size() > 1 ? metrics.lengthToPx(size, 1) / 2 : w2;
		Point2D.Double src = new Point2D.Double(node.getX(), node.getY());
		boolean vis = true;

		Tx.transform(src, src);

		double x1 = src.x - w2;
		double x2 = src.x + w2;
		double y1 = src.y - h2;
		double y2 = src.y + h2;

		if (x2 < X1)
			vis = false;
		else if (y2 < Y1)
			vis = false;
		else if (x1 > X2)
			vis = false;
		else if (y1 > Y2)
			vis = false;

		return vis;
	}

	/**
	 * Is the given sprite visible in the given area.
	 * 
	 * @param sprite
	 *            The sprite to check.
	 * @param X1
	 *            The min abscissa of the area.
	 * @param Y1
	 *            The min ordinate of the area.
	 * @param X2
	 *            The max abscissa of the area.
	 * @param Y2
	 *            The max ordinate of the area.
	 * @return True if the node lies in the given area.
	 */
	protected boolean isSpriteIn(GraphicSprite sprite, double X1, double Y1,
			double X2, double Y2) {
		if (sprite.isAttachedToNode()
				&& nodeInvisible.contains(sprite.getNodeAttachment().getId())) {
			return false;
		} else if (sprite.isAttachedToEdge()
				&& !isEdgeVisible(sprite.getEdgeAttachment())) {
			return false;
		} else {
			Values size = sprite.getStyle().getSize();
			double w2 = metrics.lengthToPx(size, 0) / 2;
			double h2 = size.size() > 1 ? metrics.lengthToPx(size, 1) / 2 : w2;
			Point2D.Double src = spritePositionPx(sprite);// new Point2D.Double(
															// sprite.getX(),
															// sprite.getY() );

			// Tx.transform( src, src );

			double x1 = src.x - w2;
			double x2 = src.x + w2;
			double y1 = src.y - h2;
			double y2 = src.y + h2;

			if (x2 < X1)
				return false;
			if (y2 < Y1)
				return false;
			if (x1 > X2)
				return false;
			if (y1 > Y2)
				return false;

			return true;
		}
	}

	protected Point2D.Double spritePositionPx(GraphicSprite sprite) {
		Point2D.Double pos = new Point2D.Double();

		return getSpritePosition(sprite, pos, Units.PX);
		// if( sprite.getUnits() == Units.PX )
		// {
		// return new Point2D.Double( sprite.getX(), sprite.getY() );
		// }
		// else if( sprite.getUnits() == Units.GU )
		// {
		// Point2D.Double pos = new Point2D.Double( sprite.getX(), sprite.getY()
		// );
		// return (Point2D.Double) Tx.transform( pos, pos );
		// }
		// else// if( sprite.getUnits() == Units.PERCENTS )
		// {
		// return new Point2D.Double(
		// (sprite.getX()/100f)*metrics.viewport.data[0],
		// (sprite.getY()/100f)*metrics.viewport.data[1] );
		// }
	}

	/**
	 * Check if a node contains the given point (x,y).
	 * 
	 * @param elt
	 *            The node.
	 * @param x
	 *            The point abscissa.
	 * @param y
	 *            The point ordinate.
	 * @return True if (x,y) is in the given element.
	 */
	protected boolean nodeContains(GraphicElement elt, double x, double y) {
		Values size = elt.getStyle().getSize();
		double w2 = metrics.lengthToPx(size, 0) / 2;
		double h2 = size.size() > 1 ? metrics.lengthToPx(size, 1) / 2 : w2;
		Point2D.Double src = new Point2D.Double(elt.getX(), elt.getY());
		Point2D.Double dst = new Point2D.Double();

		Tx.transform(src, dst);

		double x1 = dst.x - w2;
		double x2 = dst.x + w2;
		double y1 = dst.y - h2;
		double y2 = dst.y + h2;

		if (x < x1)
			return false;
		if (y < y1)
			return false;
		if (x > x2)
			return false;
		if (y > y2)
			return false;

		return true;
	}

	/**
	 * Check if a sprite contains the given point (x,y).
	 * 
	 * @param elt
	 *            The sprite.
	 * @param x
	 *            The point abscissa.
	 * @param y
	 *            The point ordinate.
	 * @return True if (x,y) is in the given element.
	 */
	protected boolean spriteContains(GraphicElement elt, double x, double y) {
		Values size = elt.getStyle().getSize();
		double w2 = metrics.lengthToPx(size, 0) / 2;
		double h2 = size.size() > 1 ? metrics.lengthToPx(size, 1) / 2 : w2;
		Point2D.Double dst = spritePositionPx((GraphicSprite) elt); // new
																	// Point2D.Double(
																	// elt.getX(),
																	// elt.getY()
																	// );
		// Point2D.Double dst = new Point2D.Double();

		// Tx.transform( src, dst );

		double x1 = dst.x - w2;
		double x2 = dst.x + w2;
		double y1 = dst.y - h2;
		double y2 = dst.y + h2;

		if (x < x1)
			return false;
		if (y < y1)
			return false;
		if (x > x2)
			return false;
		if (y > y2)
			return false;

		return true;
	}

	/**
	 * Compute the position of a sprite if it is not attached.
	 * 
	 * @param sprite
	 *            The sprite.
	 * @param pos
	 *            Where to stored the computed position, if null, the position
	 *            is created.
	 * @param units
	 *            The units the computed position must be given into.
	 * @return The same instance as pos, or a new one if pos was null.
	 */
	protected Point2D.Double getSpritePositionFree(GraphicSprite sprite,
			Point2D.Double pos, Units units) {
		if (pos == null)
			pos = new Point2D.Double();

		if (sprite.getUnits() == units) {
			pos.x = sprite.getX();
			pos.y = sprite.getY();
		} else if (units == Units.GU && sprite.getUnits() == Units.PX) {
			pos.x = sprite.getX();
			pos.y = sprite.getY();

			xT.transform(pos, pos);
		} else if (units == Units.PX && sprite.getUnits() == Units.GU) {
			pos.x = sprite.getX();
			pos.y = sprite.getY();

			Tx.transform(pos, pos);
		} else if (units == Units.GU && sprite.getUnits() == Units.PERCENTS) {
			pos.x = metrics.lo.x + (sprite.getX() / 100f)
					* metrics.graphWidthGU();
			pos.y = metrics.lo.y + (sprite.getY() / 100f)
					* metrics.graphHeightGU();
		} else if (units == Units.PX && sprite.getUnits() == Units.PERCENTS) {
			pos.x = (sprite.getX() / 100f) * metrics.viewport.data[0];
			pos.y = (sprite.getY() / 100f) * metrics.viewport.data[1];
		} else {
			throw new RuntimeException("Unhandled yet sprite positioning.");
		}

		return pos;
	}

	/**
	 * Compute the position of a sprite if attached to a node.
	 * 
	 * @param sprite
	 *            The sprite.
	 * @param pos
	 *            Where to stored the computed position, if null, the position
	 *            is created.
	 * @param units
	 *            The units the computed position must be given into.
	 * @return The same instance as pos, or a new one if pos was null.
	 */
	protected Point2D.Double getSpritePositionNode(GraphicSprite sprite,
			Point2D.Double pos, Units units) {
		if (pos == null)
			pos = new Point2D.Double();

		GraphicNode node = sprite.getNodeAttachment();
		double radius = metrics.lengthToGu(sprite.getX(), sprite.getUnits());
		double z = (double) (sprite.getZ() * (Math.PI / 180f));

		pos.x = node.x + ((double) Math.cos(z) * radius);
		pos.y = node.y + ((double) Math.sin(z) * radius);

		if (units == Units.PX)
			Tx.transform(pos, pos);

		return pos;
	}

	/**
	 * Compute the position of a sprite if attached to an edge.
	 * 
	 * @param sprite
	 *            The sprite.
	 * @param pos
	 *            Where to stored the computed position, if null, the position
	 *            is created.
	 * @param units
	 *            The units the computed position must be given into.
	 * @return The same instance as pos, or a new one if pos was null.
	 */
	protected Point2D.Double getSpritePositionEdge(GraphicSprite sprite,
			Point2D.Double pos, Units units) {
		if (pos == null)
			pos = new Point2D.Double();

		GraphicEdge edge = sprite.getEdgeAttachment();

		if (edge.isCurve()) {
			double ctrl[] = edge.getControlPoints();
			Point2 p0 = new Point2(edge.from.getX(), edge.from.getY());
			Point2 p1 = new Point2(ctrl[0], ctrl[1]);
			Point2 p2 = new Point2(ctrl[1], ctrl[2]);
			Point2 p3 = new Point2(edge.to.getX(), edge.to.getY());
			Vector2 perp = CubicCurve.perpendicular(p0, p1, p2, p3,
					sprite.getX());
			double y = metrics.lengthToGu(sprite.getY(), sprite.getUnits());

			perp.normalize();
			perp.scalarMult(y);

			pos.x = CubicCurve.eval(p0.x, p1.x, p2.x, p3.x, sprite.getX())
					- perp.data[0];
			pos.y = CubicCurve.eval(p0.y, p1.y, p2.y, p3.y, sprite.getX())
					- perp.data[1];
		} else {
			double x = ((GraphicNode) edge.getSourceNode()).x;
			double y = ((GraphicNode) edge.getSourceNode()).y;
			double dx = ((GraphicNode) edge.getTargetNode()).x - x;
			double dy = ((GraphicNode) edge.getTargetNode()).y - y;
			double d = sprite.getX(); // Percent on the edge.
			double o = metrics.lengthToGu(sprite.getY(), sprite.getUnits());
			// Offset from the position given by percent, perpendicular to the
			// edge.

			d = d > 1 ? 1 : d;
			d = d < 0 ? 0 : d;

			x += dx * d;
			y += dy * d;

			d = (double) Math.sqrt(dx * dx + dy * dy);
			dx /= d;
			dy /= d;

			x += -dy * o;
			y += dx * o;

			pos.x = x;
			pos.y = y;

			if (units == Units.PX) {
				Tx.transform(pos, pos);
			}
		}

		return pos;
	}
}