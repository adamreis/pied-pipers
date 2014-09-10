package piedpipers.smart1;

import java.util.Random;

import piedpipers.sim.Point;

public class Player extends piedpipers.sim.Player {
	static int npipers;
	
	static double pspeed = 0.49;
	static double mpspeed = 0.09;
	
	static Point dropOffPoint = new Point();
	
	static Point target = new Point();
	static int[] thetas;
	static boolean finishedRound = false;
	static boolean initi = false;
	
	public void init() {
		thetas = new int[npipers];
		dropOffPoint = new Point(dimension/4, dimension/2);
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
		// If the piper is on the wrong side of the fence,
		// move to the opening.
		Point current = pipers[id];
		Point gate = new Point(dimension/2, dimension/2);
		double ox = 0; // delta x/y
		double oy = 0;
		if (getSide(current) == 0) {
			if (finishedRound) {
				if (distance(current, dropOffPoint) > 10) {
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
		}
		
		else {
			// You're on the right side.
			// Find closest rat. Move in that direction.
			Point closestRat = findClosestRatNotInInfluence(current, rats);
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
				double dist = distance(current, closestRat);
				ox = (closestRat.x - current.x) / dist * mpspeed;
				oy = (closestRat.y - current.y) / dist * mpspeed;
				this.music = true;
				System.out.println("moved toward closest rat at " +
						closestRat.x + ", " + closestRat.y);
			}
		}
		current.x += ox;
		current.y += oy;
		return current;
	}
	
	Point findClosestRatNotInInfluence(Point current, Point[] rats) {
		double closestSoFar = Integer.MAX_VALUE;
		Point closestRat = new Point();
		// Assumed true until we find a rat not in influence
		boolean allRatsFound = true;
		for(Point rat : rats) {
			if (getSide(rat) == 0) {
				continue;
			}
			double ratDist = distance(current, rat);
			if (ratDist < closestSoFar && ratDist > 10) {
				allRatsFound = false;
				closestSoFar = ratDist;
				closestRat = rat;
			}
		}
		if (allRatsFound) {
			return null;
		}
		else {
			return closestRat;
		}
	}
	
	boolean closetoWall (Point current) {
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
