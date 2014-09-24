package piedpipers.group6redirect;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import piedpipers.sim.Point;

public class Player extends piedpipers.sim.Player {
	static int npipers;
	
	static double pspeed = 0.49999;
	static double mpspeed = 0.099999;
	static double WALK_SPEED = 0.1; // 1m/s, walking speed for rats
	
	static int predictionLookAhead = 2000; 
	
	static Point dropOffPoint;
	
	// Map of each rat's dedicated section of the board
	HashMap<Integer, int[]> boundaries= new HashMap<Integer, int[]>();
	
	ArrayList<ArrayList<Point>> predictedRatPositions;
	static ArrayList<Point> farAwayPositions;
	int[] ratThetas; // This contains current rat thetas, but is modified when we calculate new positions
	int[] lastRatThetas; // This contains the last rat thetas
	int[] currentRatThetas; // This contains the current rat thetas, unmodified
	
	static int numMoves;
	static double OPEN_LEFT; // left side of center opening
	static double OPEN_RIGHT; // right side of center opening
	
	static Point target = new Point();
	static boolean finishedRound = false;
	static boolean initi = false;
	static boolean hitTheWall = false;
	
	static String state;
	static int ratOfInterest;
	static int magnetPiperId;
	boolean[] ratsCaptured;
	boolean[] ratsInRightDirection;
	
	Point gate;
	Point magnetPoint;
	Rectangle2D goalBox;
	
	public void init() {
		dropOffPoint = new Point(dimension/2 - 5, dimension/2);
		
		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		numMoves = 0;
		
		gate = new Point(dimension/2, dimension/2);
		goalBox = new Rectangle2D.Double(dimension/2, dimension/2 - 10, 20, 20);
		magnetPoint = new Point(dimension/2 + 10, dimension/2);
		state = "default";
		
		predictedRatPositions = new ArrayList<ArrayList<Point>>();
		farAwayPositions = new ArrayList<Point>(predictionLookAhead);
		for (int i = 0; i < predictionLookAhead; i++) {
			farAwayPositions.add(new Point(Float.MAX_VALUE, Float.MAX_VALUE));
		}
	}

	static double distance(Point a, Point b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
	}

	// Return: the next position
	// my position: pipers[id-1]

	public Point move(Point[] pipers, Point[] rats, boolean[] pipermusic, int[] thetas) {
		if (!initi) {
			this.init();
			for (int i = 0; i < rats.length; i++) {
				predictedRatPositions.add(new ArrayList<Point>(predictionLookAhead));
			}
			ratsCaptured = new boolean[rats.length];
			ratsInRightDirection = new boolean[rats.length];
			magnetPiperId = pipers.length/2;
			initi = true;
		}
		numMoves++;
		npipers = pipers.length;
		ratThetas = thetas.clone();
		lastRatThetas = currentRatThetas;
		currentRatThetas = thetas.clone();
		
		Point current = pipers[id];
		double ox = 0; // delta x/y
		double oy = 0;
		
		
		switch (getSide(current)) {
		case 0:
			if (finishedRound) {
				// The round is finished
				if (distance(current, dropOffPoint) > 1) {
					this.music = true;
					Point dropOffPoint = new Point(dimension/4, dimension/2);
					double dist = distance(current, dropOffPoint);
					ox = (dropOffPoint.x - current.x) / dist * mpspeed;
					oy = (dropOffPoint.y - current.y) / dist * mpspeed;
//					System.out.println("move toward dropoff point");	
				}
			}
			else {
				// You haven't begun collecting rats yet.
				this.music = false;
				double dist = distance(current, gate);
				ox = (gate.x - current.x) / dist * pspeed;
				oy = (gate.y - current.y) / dist * pspeed;
//				System.out.println("move toward the right side");
			}
			break;
		
		default:
			// You're on the right side.
			updateRatPositions(current, rats, pipers);
			
			if (id == magnetPiperId) {
				// I'm the magnet rat!
				boolean allRatsCaptured = true;
				for (Point r : rats) {
					if (distance(current, r) > 10 && getSide(r) == 1) {
						allRatsCaptured = false;
						break;
					}
				}
				if (allRatsCaptured) {
					// move to a point just inside of the gate
					finishedRound = true;
					this.music = true;
					double dist = distance(current, gate);
					ox = (gate.x - current.x) / dist * mpspeed;
					oy = (gate.y - current.y) / dist * mpspeed;
				} else {
					this.music = true;
//					// move toward magnetPoint if needbe
//					double dist = distance(current, magnetPoint);
//					if (dist > 0.5) {
//						this.music = false;
//						ox = (magnetPoint.x - current.x) / dist * pspeed;
//						oy = (magnetPoint.y - current.y) / dist * pspeed;
//					} else {
//						this.music = true;
//						ox = oy = 0;
//					}
//					 find next rat that'll be in your box
					Point next = nextRatInGoalBox(current, rats);
					if (next == null) {
						// move to center of goal box
						next = new Point(goalBox.getCenterX(), goalBox.getCenterY());
					}
					double dist = distance(current, next);
					ox = (next.x - current.x) / dist * mpspeed;
					oy = (next.y - current.y) / dist * mpspeed;
				}
				current.x += ox;
				current.y += oy;
				return current;
			}
			
			// Set boundaries
			if (boundaries.isEmpty()) {
				int i = 0;
				for (Point piper : pipers) {
					int topYVal = i * dimension / npipers;
					int bottomYVal = topYVal + (dimension/npipers) - 1;
					int[] section = {topYVal, bottomYVal};
					// Make the ID an Integer so it can be used as a key in a HashMap
					boundaries.put(new Integer(i), section);
					i++;
				}
			}
			// Send pipers to the farthest edge of in their respective section until they hit the wall
//			if (!hitTheWall) {
//				hitTheWall = closeToWall(current);
//				this.music = false;
//				Point piperStartPoint = getPiperStartPoint(id);
//				double dist = distance(current, piperStartPoint);
//				ox = (piperStartPoint.x - current.x) / dist * pspeed;
//				oy = (piperStartPoint.y - current.y) / dist * pspeed;	
//			}
//			else {
				if (state.equals("redirecting")) {
					ox = 0;
					oy = 0;
					if (this.music == true) {
						this.music = false;
					} else {
						// let's see if this rat is going in the right direction
						if (anyRatNearMeGoingTheRightDirectionOrNoneAtAll(current, rats)) {
							state = "default";
//							System.out.println("changed state to 'default'");
						} else {
							this.music = true;
						}
						
					}
					// redirect and switch on and off accordingly
				} else {
					// Find closest rat that's not heading in the direction we want it to be. Move in that direction.
					Point closestRat = findClosestRatGoingInWrongDirection(pipers[id], rats, pipers);
					if (closestRat == null) {
//						System.out.println("clossetRat == null");
						// All rats have been found. Just chill out for now.
//						finishedRound = true;
						this.music = false;
						ox = 0;
						oy = 0;	
					}
					else if (distance(closestRat, current) <= 9.8){
						ox = 0;
						oy = 0;
						this.music = true;
						state = "redirecting";
//						System.out.println("changed state to 'redirecting'");
					} else {
						System.out.println("I am rat " + id + " and closestRat is at (" + closestRat.x + ", " + closestRat.y + ")");
						// All Rats have not been found; continue to catch em.
						double dist = distance(current, closestRat);
						ox = (closestRat.x - current.x) / dist * pspeed;
						oy = (closestRat.y - current.y) / dist * pspeed;
						this.music = false;
//						System.out.println("moved toward closest rat at " +
//								closestRat.x + ", " + closestRat.y);
					}
				}
			}
//		}
		current.x += ox;
		current.y += oy;
		return current;
	}
	
	
	Point getPiperStartPoint(int id) {
		int[] boundary = boundaries.get(id);
		int startX = dimension;
		int startY = boundary[0] + (boundary[1] - boundary[0]) / 2;
		return new Point(startX, startY);
	}
	
	void updateRatPositions(Point current, Point[] rats, Point[] pipers){
		// First, check if all rats have been captured

		for (int i = 0; i < rats.length; i++) {
			boolean thisRatFound = false;
			boolean thisRatInRightDirection = false;
			if (getSide(rats[i]) == 0 || distance(rats[i], pipers[magnetPiperId]) <= 10) {
				thisRatFound = true;
			} else {
				for (Point p: pipers) {
					if (p != current && distance(rats[i], p) <= 10) {
						thisRatFound = true;
						break;
					}
				}
			}
			ratsInRightDirection[i] = goingInRightDirection(i, rats[i]);
			ratsCaptured[i] = thisRatFound;
		}


		// Update predicted positions
		for (int i = 0; i < rats.length; i++) {
			if (ratsCaptured[i] == true) {
				predictedRatPositions.get(i).clear();
				continue;
			} else if ((lastRatThetas[i] == currentRatThetas[i]) && (predictedRatPositions.get(i).size() > 0)) {
//				System.out.println("we optimizing!");
				Point lastPos = predictedRatPositions.get(i).get(predictionLookAhead - 1);
				predictedRatPositions.get(i).remove(0);
				predictedRatPositions.get(i).add(getNewPosition(lastPos, ratThetas[i], i));
			} 
			else {
			
				predictedRatPositions.get(i).clear();
				Point oldPosition = rats[i];
				
				for (int j = 0; j < predictionLookAhead; j++) {
					Point newPosition = getNewPosition(oldPosition, ratThetas[i], i);
					predictedRatPositions.get(i).add(newPosition);
					oldPosition = newPosition;
				}
			}
		}
	}
	
	Point nextRatInGoalBox(Point current, Point[] rats) {
		Point predictedPoint;
		for (int i = 0; i < predictionLookAhead; i++) {
			for (int j = 0; j < rats.length; j++) {
				if (ratsCaptured[j]) {
					continue;
				}
				predictedPoint = predictedRatPositions.get(j).get(i);
				
				if (goalBox.contains(predictedPoint.x, predictedPoint.y) && distance(current, predictedPoint) > 10) {
					return predictedPoint;
				}
			}
		}
		return null;
	}
	
	Point[] getAllRatsInNTicks(int ticks, Point[] rats) {
		Point[] ratPositions = new Point[rats.length];
		for(int i=0; i < rats.length; i++ ) {
			if(! predictedRatPositions.get(i).isEmpty() ) {
				ratPositions[i] = predictedRatPositions.get(i).get(ticks);
			} else {
				ratPositions[i] = rats[i];
			}
		}
		return ratPositions;
	}
	
	double[] findIntersectPointTime(Point current, Point[] rats, int ratNum) {
		for (int i = 0; i < predictionLookAhead; i++) {
			Point predictedPoint = predictedRatPositions.get(ratNum).get(i);
			double ratDist = distance(current, predictedPoint);
			double[] pointTime = {predictedPoint.x, predictedPoint.y, i};
			if (ratDist < (10 + i * pspeed)) {
				return pointTime;
			}
		}
		// not intersecting in time!
		return null;
	}
	
	// TODO
	Point findNextRatToRedirect(Point current, Point[] rats, int depth) {
		Point currentIter = current;
		Point[] ratsIter = rats;
		int ticks = 0;
		Point closestRat = new Point();
		Point firstRat = new Point();
		for(int i = 0; i < depth; i++) {
			// greedily find next rat position, phase 1
			double[] closestRatPos = findClosestOneRatToRedirect(currentIter, ratsIter);
			// couldn't even get to depth using greedy...stop here
			if(closestRatPos == null) {
				continue;
			}
			closestRat = new Point(closestRatPos[0], closestRatPos[1]);
			if( i == 0) {
				firstRat = closestRat;
			}
			ticks += (int) closestRatPos[2];
			// do an theoretical update
			currentIter = closestRat;
			ratsIter = getAllRatsInNTicks(ticks, rats);
		}
		System.out.println("# Ticks to get to " + depth + " rats: " + ticks);
		double[] intersect = new double[3];
		// go through all rats...phase 2
		for(int ratIdx = 0; ratIdx < rats.length; ratIdx++ ) {
			Point current2Iter = current;
			Point[] rats2Iter = rats;
			int ticks2 = 0;
			Point firstRat2 = new Point();
			boolean deepEnough = true;
			// as deep as needed / reasonable...
			for(int depthIdx = 0; depthIdx < depth; depthIdx++) {
				intersect = findIntersectPointTime(current2Iter, rats2Iter, ratIdx);
				// keep calculating only if time is smaller and intersection is still possible
				if(intersect != null && intersect[2] < ticks ) {
					if( depthIdx == 0) {
						firstRat2 = new Point(intersect[0], intersect[1]);
					}
					ticks2 += (int) intersect[2];
					current2Iter = new Point(intersect[0], intersect[1]);
					rats2Iter = getAllRatsInNTicks(ticks2, rats);
				} else {
					deepEnough = false;
					break;
				}
			}
			// if actually got to the necessary # of rats & on better time
			if(deepEnough && ticks2 < ticks) {
				ticks = ticks2;
				firstRat = firstRat2;
			}
		}
		return firstRat;
	}
	
	double[] findClosestOneRatToRedirect(Point current, Point[] rats) {
		for (int i = 0; i < predictionLookAhead; i++) {
			for (int j = 0; j < rats.length; j++) {
				if (ratsCaptured[j] || ratsInRightDirection[j]) {
					continue;
				}
				Point predictedPoint = predictedRatPositions.get(j).get(i);
				double[] pointTime = {predictedPoint.x, predictedPoint.y, i};
				double ratDist = distance(current, predictedPoint);		
				if (ratDist <= 10) {
					// check to see if this rat, or any other rat near me, is going in the right direction
					if (anyRatNearMeGoingTheRightDirectionOrNoneAtAll(current, rats)) {
						continue;
					} else {
						ratOfInterest = j;
						return pointTime;
					}
				} else if (ratDist < (10 + i * pspeed))  {
//					System.out.println("returning future point " + i + " for rat #" + j);
					return pointTime;
				}
			}
		}
		System.out.println("RETURNING NULL");
		return null;
	}
	
	Point findClosestRatGoingInWrongDirection(Point current, Point[] rats, Point[] pipers) {
//		for (int i = 0; i < predictionLookAhead; i++) {
//			for (int j = 0; j < rats.length; j++) {
//				if (ratsCaptured[j] || ratsInRightDirection[j]) {
//					continue;
//				}
//				Point predictedPoint = predictedRatPositions.get(j).get(i);
//				double ratDist = distance(current, predictedPoint);		
//				if (ratDist <= 10) {
//					// check to see if this rat, or any other rat near me, is going in the right direction
//					if (anyRatNearMeGoingTheRightDirectionOrNoneAtAll(current, rats)) {
//						continue;
//					} else {
//						ratOfInterest = j;
//						return predictedPoint;
//					}
//				} else if (ratDist < (10 + i * pspeed))  {
////					System.out.println("returning future point " + i + " for rat #" + j);
//					return predictedPoint;
//				}
//			}
//		}
		Point nextRat = findNextRatToRedirect(current, rats, 5); //Point current, Point[] rats, int depth
		System.out.println("FOUND NEXT RAT");
		if( nextRat.x != 0 || nextRat.y != 0 ) {
			return nextRat;
		}
		// if findNextRatToRedirect doesn't have a rat to give...
		else {
			System.out.println("findClosestRatNotInInfluence should not get to this point!  Here's what ratsFound looks like:");
	//		System.out.println("ratsFound: " + Arrays.toString(ratsCaptured));
			double closestSoFar = Integer.MAX_VALUE;
			Point closestRat = new Point();
			// Assumed true until we find a rat not in influence
			for(int i = 0; i < rats.length; i++) {
				if (ratsCaptured[i] == true || ratsInRightDirection[i]) {
					continue;
				}
				double ratDist = distance(current, rats[i]);
				if (ratDist < closestSoFar && ratDist > 10) {
					closestSoFar = ratDist;
					closestRat = rats[i];
				}
			}
			if (closestSoFar < Integer.MAX_VALUE) {
				return closestRat;
			} else {
				return null;
			}
		}
	}
	
	boolean anyRatNearMeGoingTheRightDirectionOrNoneAtAll(Point current, Point[] rats) {
		boolean noRatsNearby = true;
		for (int i = 0; i < rats.length; i++) {
			if (distance(current, rats[i]) <= 10 ) {
				noRatsNearby = false;
				if(ratsInRightDirection[i]) {
					return true;
				}
			}
		}
		return noRatsNearby;
	}
	
	boolean goingInRightDirection(int ratIndex, Point ratPosition) {
		int ratAngle = currentRatThetas[ratIndex];
		double ox = dimension * 2 * Math.sin(ratAngle * Math.PI / 180);
		double oy = dimension * 2 * Math.cos(ratAngle * Math.PI / 180);
		
		Line2D newLine = new Line2D.Double(ratPosition.x, ratPosition.y, ratPosition.x + ox, ratPosition.y + oy);
		return newLine.intersects(goalBox);		
	}
	
	ArrayList<Point> getRatsInSection(int[] sectionCoords, Point[] rats) {
		ArrayList<Point> ratsInSection = new ArrayList<Point>();
		int topCoord = sectionCoords[0];
		int bottomCoord = sectionCoords[1];
		for (Point rat : rats) {
			if (getSide(rat) == 0) {
				continue;
			}
			if (rat.y > topCoord && rat.y < bottomCoord) {
				ratsInSection.add(rat);
			}
		}
		return ratsInSection;
	}
	
	boolean closeToWall (Point current) {
		boolean wall = false;
		if (Math.abs(current.x-dimension)<pspeed) {
			wall = true;
		}
		if (Math.abs(current.y-dimension)<pspeed) {
			wall = true;
		}
		if (Math.abs(current.y)<pspeed) {
			wall = true;
		}
		return wall;
	}
	int getSide(double x, double y) {
		if (x < dimension * 0.5)
			return 0;
		else if (x > dimension * 0.5)
			return 1;
		else
			return 2;
	}

	int getSide(Point p) {
		return getSide(p.x, p.y);
	}
	
	// update the current point according to the offsets
	Point getNewPosition(Point ratPosition, double ratAngle, int ratId) {
		double ox = WALK_SPEED * Math.sin(ratAngle * Math.PI / 180);
		double oy = WALK_SPEED * Math.cos(ratAngle * Math.PI / 180);
		return updatePosition(ratPosition, ox, oy, ratId);
	}
	
	Point updatePosition(Point now, double ox, double oy, int rat) {
		double nx = now.x + ox, ny = now.y + oy;
		int id_rat = rat;
		
		// hit the left fence
		if (nx < 0) {
			// System.err.println("RAT HITS THE LEFT FENCE!!!");
			// move the point to the left fence
			Point temp = new Point(0, now.y);
			// how much we have already moved in x-axis?
			double moved = 0 - now.x;
			// how much we still need to move
			// BUT in opposite direction
			double ox2 = -(ox - moved);
			//Random random = new Random();
			
			//int theta = random.nextInt(360);
			int theta = -ratThetas[rat];
			ratThetas[rat] = theta;
			return updatePosition(temp, ox2, oy, id_rat);
		}
		// hit the right fence
		if (nx > dimension) {
			// System.err.println("RAT HITS THE RIGHT FENCE!!!");
			// move the point to the right fence
			Point temp = new Point(dimension, now.y);
			double moved = (dimension - now.x);
			double ox2 = -(ox - moved);
			//Random random = new Random();
			
			int theta = -ratThetas[rat];
			ratThetas[rat] = theta;
			return updatePosition(temp, ox2, oy, id_rat);
		}
		// hit the up fence
		if (ny < 0) {
			// System.err.println("RAT HITS THE UP FENCE!!!");
			// move the point to the up fence
			Point temp = new Point(now.x, 0);
			double moved = 0 - now.y;
			double oy2 = -(oy - moved);
			//Random random = new Random();
		
			int theta = 180-ratThetas[rat];
			ratThetas[rat] = theta;
			return updatePosition(temp, ox, oy2, id_rat);
		}
		// hit the bottom fence
		if (ny > dimension) {
			// System.err.println("RAT HITS THE BOTTOM FENCE!!!");
			Point temp = new Point(now.x, dimension);
			double moved = (dimension - now.y);
			double oy2 = -(oy - moved);
			//Random random = new Random();
			int theta = 180-ratThetas[rat];
			ratThetas[rat] = theta;
			return updatePosition(temp, ox, oy2, id_rat);
		}
		assert nx >= 0 && nx <= dimension;
		assert ny >= 0 && ny <= dimension;
		// hit the middle fence
		if (hitTheFence(now.x, now.y, nx, ny)) {
			// System.err.println("SHEEP HITS THE CENTER FENCE!!!");
			// System.err.println(nx + " " + ny);
			// System.err.println(ox + " " + oy);
			// move the point to the fence
			Point temp = new Point(dimension/2, now.y);
			double moved = (dimension/2 - now.x);
			double ox2 = -(ox - moved);
			//Random random = new Random();
			int theta = -ratThetas[rat];
			ratThetas[rat] = theta;
			return updatePosition(temp, ox2, oy, id_rat);
		}
		// otherwise, we are good
		return new Point(nx, ny);
	}
	
	boolean hitTheFence(double x1, double y1, double x2, double y2) {
		// on the same side
		if (getSide(x1, y1) == getSide(x2, y2))
			return false;

		// one point is on the fence
		if (getSide(x1, y1) == 2 || getSide(x2, y2) == 2)
			return false;

		// compute the intersection with (50, y3)
		// (y3-y1)/(50-x1) = (y2-y1)/(x2-x1)

		double y3 = (y2 - y1) / (x2 - x1) * (dimension/2 - x1) + y1;

		assert y3 >= 0 && y3 <= dimension;

		// pass the openning?
		if (y3 >= OPEN_LEFT && y3 <= OPEN_RIGHT) 
			return false;
		else {
//				System.out.printf("hit the medium fence");
			return true;
		}
	}


}
