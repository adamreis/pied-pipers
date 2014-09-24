package piedpipers.group6redirect;

import java.awt.geom.*;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import piedpipers.sim.Point;

public class RedirectPlayer extends piedpipers.sim.Player {
	static int npipers;
	
	static double pspeed = 0.49999999;
	static double mpspeed = 0.099999999;
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
	
	Polygon[] partitions;
	Polygon wholeRightSide;
	
	Point runningAwayPoint;
	int runningAwayCount;
	
	Point gate;
	Point magnetPoint;
	Rectangle2D goalBox;
	
	public void init() {
		dropOffPoint = new Point(dimension/2 - 5, dimension/2);
		
		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		numMoves = 0;
		
		gate = new Point(dimension/2, dimension/2);
		goalBox = new Rectangle2D.Double(dimension/2, dimension/2 - 12, 25, 25);
		magnetPoint = new Point(dimension/2 + 10, dimension/2);
		state = "default";
		
		runningAwayPoint = new Point(3 * dimension/2, dimension/2);
		runningAwayCount = 0;
		
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
//		System.out.println("I am rat " + id + " ");
		if (!initi) {
			this.init();
			for (int i = 0; i < rats.length; i++) {
				predictedRatPositions.add(new ArrayList<Point>(predictionLookAhead));
			}
			ratsCaptured = new boolean[rats.length];
			ratsInRightDirection = new boolean[rats.length];
			magnetPiperId = pipers.length - 1;
			initi = true;
			
			int[] wholeRightX = {dimension/2, dimension, dimension, dimension/2};
			int[] wholeRightY = {0, 0, dimension, dimension};
			wholeRightSide = new Polygon(wholeRightX, wholeRightY, 4);
			
			partitions = new Polygon[pipers.length - 1];
			double partitionAngle = Math.PI/(pipers.length - 1);
			int[] x = new int[3];
			int[] y = new int[3];
			x[0] = dimension/2;
			y[0] = dimension/2;
			for (int i = 0; i < pipers.length - 1; i++) {
				double angle = (i + 1) * partitionAngle;
				int yHeight = (int) ((dimension/2)/Math.tan(angle));
				if (i == 0 ){
					// top section
					x[1] = dimension/2;
					y[1] = 0;
					
					if (yHeight >= dimension/2) {
						x[2] = dimension;
						y[2] = dimension/2 - yHeight;
					} else {
						y[2] = 0;
						x[2] = (int)(dimension/2 * Math.tan(angle));
					}
					
				} else if (i == pipers.length - 2) {
					// bottom section
					x[1] = new Integer(x[2]);
					y[1] = new Integer(y[2]);
					x[2] = dimension/2;
					y[2] = dimension;
//					
//					if (yHeight <= -1 * dimension/2) {
//						x[1] = dimension;
//						y[1] = dimension/2 - yHeight;
//					} else {
//						y[2] = dimension;
//						x[2] = (int)(dimension/2 * Math.tan(angle));
//					}
					
					
				} else {
					// everything else
					x[1] = dimension;
					x[2] = dimension;
					y[1] = y[2];
					y[2] = dimension/2 - yHeight;
				}
//				System.out.println("Partition shape for rat #" + i);
//				System.out.println("x:" + Arrays.toString(x));
//				System.out.println("y:" + Arrays.toString(y));
				partitions[i] = new Polygon(x, y, 3);
			}
			
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
//					System.out.println("redirecting");
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
				} else if (runningAwayCount > 0){
					runningAwayCount--;
					this.music = false;
					
					double dist = distance(runningAwayPoint, current);
					ox = (runningAwayPoint.x - current.x) / dist * pspeed;
					oy = (runningAwayPoint.y - current.y) / dist * pspeed;
				} else if (tooCloseToOtherPiper(id, pipers)){
					// Am I too close to any other pipers (with lower id's)?  If so, run away!
					runningAwayCount = 20;
//					System.out.println("Running away!");
					this.music = false;
					
					double dist = distance(runningAwayPoint, current);
					ox = (runningAwayPoint.x - current.x) / dist * pspeed;
					oy = (runningAwayPoint.y - current.y) / dist * pspeed;
					
				} else {	
					
					// Find closest rat that's not heading in the direction we want it to be. Move in that direction.
					Point closestRat = findClosestRatGoingInWrongDirectionInPartition(pipers[id], rats, pipers);
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
//						System.out.println("closestRat is at (" + closestRat.x + ", " + closestRat.y + "). distance = " + distance(current, closestRat));
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
	
	boolean tooCloseToOtherPiper(int id, Point[] pipers) {
		for (int i = 0; i < id; i++) {
			if (distance(pipers[id], pipers[i]) < 5) {
				return true;
			}
		}
		return false;
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
			if (getSide(rats[i]) == 0 || distance(rats[i], pipers[magnetPiperId]) <= 10) {
				thisRatFound = true;
			} else {
//				for (Point p: pipers) {
//					if (p != current && distance(rats[i], p) <= 10) {
//						thisRatFound = true;
//						break;
//					}
//				}
				for (int j = 0; j < pipers.length; j++) {
					if (j != id && distance(rats[i], pipers[j]) <= 10) {
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
	
	Point findClosestRatGoingInWrongDirectionInPartition(Point current, Point[] rats, Point[] pipers) {
		for (int i = 0; i < predictionLookAhead; i++) {
			for (int j = 0; j < rats.length; j++) {
				if (ratsCaptured[j] || ratsInRightDirection[j] || !partitions[id].contains(rats[j].x, rats[j].y)) {
					continue;
				}
				Point predictedPoint = predictedRatPositions.get(j).get(i);
				double ratDist = distance(current, predictedPoint);		
				if (ratDist <= 10) {
					// check to see if this rat, or any other rat near me, is going in the right direction
					if (anyRatNearMeGoingTheRightDirectionOrNoneAtAll(current, rats)) {
						continue;
					} else {
						ratOfInterest = j;
						return predictedPoint;
					}
				} else if (ratDist < (10 + i * pspeed))  {
//					System.out.println("returning future point " + i + " for rat #" + j);
					return predictedPoint;
				}
			}
		}
		
		if (partitions[id] != wholeRightSide) {
//			System.out.println("expanding partition for piper #" + id);
			partitions[id] = wholeRightSide;
			return findClosestRatGoingInWrongDirectionInPartition(current, rats, pipers);
		}
		
//		System.out.println("findClosestRatNotInInfluence should not get to this point!  Here's what ratsCaptured looks like:");
//		System.out.println("ratsCaptured: " + Arrays.toString(ratsCaptured));
//		System.out.println("rightDirectn: " + Arrays.toString(ratsInRightDirection));
		
		double closestSoFar = Integer.MAX_VALUE;
		Point closestRat = new Point();
		// Assumed true until we find a rat not in influence
		for(int i = 0; i < rats.length; i++) {
			if (ratsCaptured[i] == true || ratsInRightDirection[i]) {
				continue;
			}
//			System.out.println("I found a rat that isn't captured or in the right direction.  its ID is " + i);
			double ratDist = distance(current, rats[i]);
			if (ratDist < closestSoFar) {
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
