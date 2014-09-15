package piedpipers.group6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import piedpipers.sim.Point;

public class Player extends piedpipers.sim.Player {
	static int npipers;
	
	static double pspeed = 0.49;
	static double mpspeed = 0.09;
	
	static Point dropOffPoint = new Point();
	
	// Map of each rat's dedicated section of the board
	HashMap<Integer, int[]> boundaries= new HashMap<Integer, int[]>();
	
	static Point target = new Point();
	static int[] thetas;
	static boolean finishedRound = false;
	static boolean initi = false;
	static boolean hitTheWall = false;
	
	public void init() {
		dropOffPoint = new Point(0, dimension/2);
		/*for (int i=0; i< npipers; i++) {
			Random random = new Random();
			int theta = random.nextInt(180);
			thetas[i]=theta;
			System.out.println(thetas[i]);
		}*/
	}

	static double distance(Point a, Point b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
	}

	// Return: the next position
	// my position: pipers[id-1]

	public Point move(Point[] pipers, // positions of pipers
			Point[] rats) { // positions of the rats
		if (!initi) {
			this.init();initi = true;
		}
		npipers = pipers.length;
		
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
				Point closestRat = findClosestRatNotInAnyInfluence(id, pipers, rats);
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
	
	Point findClosestRatNotInAnyInfluence(int id, Point[] pipers, Point[] rats) {
		double closestSoFar = Integer.MAX_VALUE;
		Point closestRat = new Point();
		// Assumed true until we find a rat not in influence
		boolean allRatsFound = true;
		for(Point rat : rats) {
			boolean ratAlreadyFound = false;
			
			// Calculate distances between all rats and all pipers
			double[] ratDistanceToPipers = new double[pipers.length];
			for (int i = 0; i<ratDistanceToPipers.length; i++) {
				double ratToPiperI = distance(pipers[i], rat);
				ratDistanceToPipers[i] = ratToPiperI;
				// If the given rat is on the other side of the fence, skip it
				if (getSide(rat.x, rat.y) == 0) {
					ratAlreadyFound = true;
				}
				// If the given rat is already in the influence of a piper,
				// skip it
				if (ratToPiperI < 10){
					ratAlreadyFound = true;
				}
			}
			// If the given rat is up for grabs, check if it's closest.
			if (!ratAlreadyFound) {
				if (ratDistanceToPipers[id] < closestSoFar) {
					allRatsFound = false;
					closestSoFar = ratDistanceToPipers[id];
					closestRat = rat;
				}
			}	
		}
		if (allRatsFound) {
			return null;
		}
		else {
			return closestRat;
		}
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


}
