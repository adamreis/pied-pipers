package piedpipers.group6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import piedpipers.sim.Point;

public class Player extends piedpipers.sim.Player {
	static int npipers;
	
	static double pspeed = 0.49;
	static double mpspeed = 0.09;
	static double WALK_SPEED = 0.1; // 1m/s, walking speed for rats
	
	static int predictionLookAhead = 2000; 
	
	static Point dropOffPoint = new Point();
	
	// Map of each rat's dedicated section of the board
	HashMap<Integer, int[]> boundaries= new HashMap<Integer, int[]>();
	
	static ArrayList<ArrayList<Point>> predictedRatPositions;
	int[] ratThetas;
	static int numMoves;
	static double OPEN_LEFT; // left side of center opening
	static double OPEN_RIGHT; // right side of center opening
	
	static Point target = new Point();
	static int[] thetas;
	static boolean finishedRound = false;
	static boolean initi = false;
	static boolean hitTheWall = false;
	
	public void init() {
		dropOffPoint = new Point(0, dimension/2);
		
		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		numMoves = 0;
		
		predictedRatPositions = new ArrayList<ArrayList<Point>>();
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
			initi = true;
		}
		numMoves++;
		npipers = pipers.length;
		ratThetas = thetas.clone();
		
		// If the piper is on the wrong side of the fence,
		// it's either the end of the round or the beginning.
		Point current = pipers[id];
		Point gate = new Point(dimension/2, dimension/2);
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
					System.out.println("move toward dropoff point");	
				}
				else {
					// Stop playing.
					this.music = false;	
				}
			}
			else {
				// You haven't begun collecting rats yet.
				this.music = false;
				double dist = distance(current, gate);
				assert dist > 0;
				ox = (gate.x - current.x) / dist * pspeed;
				oy = (gate.y - current.y) / dist * pspeed;
				System.out.println("move toward the right side");
			}
		break;
		
		default:
			// You're on the right side.

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
			if (!hitTheWall) {
				hitTheWall = closeToWall(current);
				this.music = false;
				Point piperStartPoint = getPiperStartPoint(id);
				double dist = distance(current, piperStartPoint);
				ox = (piperStartPoint.x - current.x) / dist * pspeed;
				oy = (piperStartPoint.y - current.y) / dist * pspeed;	
			}
			else {
				// Find closest rat. Move in that direction.
				Point closestRat = findClosestRatNotInInfluence(pipers[id], rats, pipers);
				if (closestRat == null) {
					// All rats have been found. Move back toward gate.
					finishedRound = true;
					this.music = true;
					double dist = distance(current, gate);
					assert dist > 0;
					ox = (gate.x - current.x) / dist * mpspeed;
					oy = (gate.y - current.y) / dist * mpspeed;
					System.out.println("move toward the left side");	
				}
				else {
					// All Rats have not been found; continue to catch em.
					double dist = distance(current, closestRat);
					ox = (closestRat.x - current.x) / dist * mpspeed;
					oy = (closestRat.y - current.y) / dist * mpspeed;
					this.music = true;
					System.out.println("moved toward closest rat at " +
							closestRat.x + ", " + closestRat.y);
				}
			}
		}
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
	
	Point findClosestRatNotInInfluence(Point current, Point[] rats, Point[] pipers) {
		// Update predicted positions
		for (int i = 0; i < rats.length; i++) {
			predictedRatPositions.get(i).clear();
			Point oldPosition = rats[i];
			
			// If a rat is already in your control, then don't bother calculate future positions!
			if (distance(pipers[id], oldPosition) < 10) {
				for (int j = 0; j < predictionLookAhead; j++) {
					predictedRatPositions.get(i).add(oldPosition);
				}
				continue;
			}
			for (int j = 0; j < predictionLookAhead; j++) {
				Point newPosition = getNewPosition(oldPosition, ratThetas[i], i);
				predictedRatPositions.get(i).add(newPosition);
				oldPosition = newPosition;
			}
		}
		
		// First, check if all rats have been captured
		boolean allRatsFound = true;
		for (Point r: rats) {
			boolean thisRatFound = false;
			for (Point p: pipers) {
				if (distance(r, p) <= 10) {
					thisRatFound = true;
					break;
				}
			}
			if (!thisRatFound) {
				allRatsFound = false;
				break;
			}
		}
		if (allRatsFound) {
			return null;
		}
		
		
		for (int i = 0; i < predictionLookAhead; i++) {
			for (ArrayList<Point> predicted : predictedRatPositions) {
				double ratDist = distance(current, predicted.get(i));
				if ((ratDist > 10) && (ratDist < (10 + i * mpspeed))) {
					System.out.println("returned at i = " + i);
					System.out.println("ratDist = " + ratDist);
					return predicted.get(i);
				}
			}
		}
		System.out.println("findClosestRatNotInInfluence should not get to this point!");
		double closestSoFar = Integer.MAX_VALUE;
		Point closestRat = new Point();
		// Assumed true until we find a rat not in influence
		for(Point rat : rats) {
			if (getSide(rat) == 0) {
				continue;
			}
			double ratDist = distance(current, rat);
			if (ratDist < closestSoFar && ratDist > 10) {
				closestSoFar = ratDist;
				closestRat = rat;
			}
		}

		return closestRat;
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