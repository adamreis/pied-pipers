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
	
	static double piperDropDistance = 6.0;
	
	static Point dropOffPoint = new Point();
	
	// Map of each rat's dedicated section of the board
	HashMap<Integer, int[]> boundaries= new HashMap<Integer, int[]>();
	
	static ArrayList<ArrayList<Point>> predictedRatPositions;
	int[] ratThetas; // This contains current rat thetas, but is modified when we calculate new positions
	int[] lastRatThetas; // This contains the last rat thetas
	int[] currentRatThetas; // This contains the current rat thetas, unmodified
	
	static double OPEN_LEFT; // left side of center opening
	static double OPEN_RIGHT; // right side of center opening
	
	static Point target = new Point();
	static int[] thetas;
	static boolean finishedRound = false;
	static boolean initi = false;
	static boolean hitTheWall = false;
	static boolean droppedRats = false;
	
	static Point targetRat;
	
	public void init() {
		dropOffPoint = new Point(0, dimension/2);
		
		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		
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
		npipers = pipers.length;
		ratThetas = thetas.clone();
		lastRatThetas = currentRatThetas;
		currentRatThetas = thetas.clone();
		
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
//					System.out.println("move toward dropoff point");	
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
//				System.out.println("move toward the right side");
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
			// You've just entered the right hand side.
			// Send pipers to the farthest edge of in their respective section until they hit the wall
			if (!hitTheWall) {
				hitTheWall = closeToWall(current);
				this.music = false;
				Point piperStartPoint = getPiperStartPoint(id);
				double dist = distance(current, piperStartPoint);
				ox = (piperStartPoint.x - current.x) / dist * pspeed;
				oy = (piperStartPoint.y - current.y) / dist * pspeed;	
			}
			// If you've just handed off rats to another piper, you want to go for the rat that's farthest away.
			else if (droppedRats) {
				this.music = false;
				if (targetRat == null) {
					targetRat = findFarthestRat(current, rats, pipers);
				}
				double dist = distance(current, targetRat);
				ox = (targetRat.x - current.x) / dist * pspeed;
				oy = (targetRat.y - current.y) / dist * pspeed;	
				if (dist < 10) {
					this.music = true;
					droppedRats = false;
				}
			}
			else {
				// See if there's a playing piper nearby and if you are carrying rats
				int closestPiperId = findClosestPiper(id, pipers);
				if( closestPiperId != -1
					&& closestPiperId < id
					&& pipermusic[closestPiperId]
					&& isInfluencingRats(current, rats) 
					&& distance(current, pipers[closestPiperId]) < piperDropDistance) {
					// if the piper close by is senior: drop the rats & run off
					this.music = false;
					droppedRats = true;
				} else {
					// Find closest rat. Move in that direction.
					Point closestRat = findClosestRat(current, rats, pipers);
					if (closestRat == null) {
						// All rats have been found. Move back toward gate.
						finishedRound = true;
						this.music = true;
						double dist = distance(current, gate);
						assert dist > 0;
						ox = (gate.x - current.x) / dist * mpspeed;
						oy = (gate.y - current.y) / dist * mpspeed;
//						System.out.println("move toward the left side");	
					}
					else {
						// If the rat you're going for is closer to another piper, go for the rat that's
						// farthest away instead.
						for (Point piper : pipers) {
							if (piper == current) {
								continue;
							}
							if (distance(piper, closestRat) < distance(closestRat, current)) {
								closestRat = findFarthestRat(current, rats, pipers);
								break;
							}
						}
						// All Rats have not been found; continue to catch em.
						double dist = distance(current, closestRat);
						
						if( isInfluencingRats(current, rats) ) {
							this.music = true;
							ox = (closestRat.x - current.x) / dist * mpspeed;
							oy = (closestRat.y - current.y) / dist * mpspeed;
						} else {
							this.music = false;
							ox = (closestRat.x - current.x) / dist * pspeed;
							oy = (closestRat.y - current.y) / dist * pspeed;
						}
//						System.out.println("moved toward closest rat at " +
//								closestRat.x + ", " + closestRat.y);
					}
				}
			}
		}
		current.x += ox;
		current.y += oy;
		return current;
	}
	
	Point findFarthestRat(Point current, Point[] rats, Point[] pipers) {
		return findXestRatNotInInfluence("far", current, rats, pipers);
	}
	
	Point findClosestRat(Point current, Point[] rats, Point[] pipers) {
		return findXestRatNotInInfluence("close", current, rats, pipers);
	}
	
	Point getPiperStartPoint(int id) {
		int[] boundary = boundaries.get(id);
		int startX = dimension;
		int startY = boundary[0] + (boundary[1] - boundary[0]) / 2;
		return new Point(startX, startY);
	}
	
	int findClosestPiper(int currentId, Point[] pipers) {
		double distanceConsidered = 10.0;
		for( int i = 0; i < currentId; i++ ) {
			if( currentId != i && distance(pipers[currentId], pipers[i]) < distanceConsidered ) {
				return i;
			}
		}
		return -1;
	}
	
	Point findXestRatNotInInfluence(String X, Point current, Point[] rats, Point[] pipers) {
		// if X is "far", find the farthest rat from the current point.
		// if X is "close", find the closest rat from the current point.
		
		// First, generate list of rats that have already been found.
		// If they've been found, don't generate future position.
		boolean[] ratsFound = new boolean[rats.length];
		boolean allRatsFound = true;
		for (int i = 0; i < rats.length; i++) {
			boolean thisRatFound = false;
			if (getSide(rats[i]) == 0) {
				thisRatFound = true;
			} else {
				for (Point p: pipers) {
					if (distance(rats[i], p) <= 10) {
						thisRatFound = true;
						break;
					}
				}
			}
			ratsFound[i] = thisRatFound;
			if (!thisRatFound) {
				allRatsFound = false;
			}
		}
		if (allRatsFound) {
			return null;
		}
		
		// Update predicted positions
		for (int i = 0; i < rats.length; i++) {
			if (ratsFound[i] == true) { 
				continue;
			} else if ((lastRatThetas[i] == currentRatThetas[i]) && (predictedRatPositions.get(i).size() > 0)) {
//						System.out.println("we optimizing!");
				Point lastPos = predictedRatPositions.get(i).get(predictionLookAhead - 1);
				predictedRatPositions.get(i).remove(0);
				predictedRatPositions.get(i).add(getNewPosition(lastPos, ratThetas[i], i));
			} else {
			
				predictedRatPositions.get(i).clear();
				Point oldPosition = rats[i];
				
				for (int j = 0; j < predictionLookAhead; j++) {
					Point newPosition = getNewPosition(oldPosition, ratThetas[i], i);
					predictedRatPositions.get(i).add(newPosition);
					oldPosition = newPosition;
				}
			}
		}
		
		if (X.equals("far")) {
			// find the rat that's farthest away at time 0
			// calculate distance to that rat
			double farthestRatDistance = 0;
			int farthestRatIndex = -1;
			for (int i = 0; i< rats.length; i++) {
				if (ratsFound[i] == true) {
					continue;
				}
				if (distance(current, rats[i]) > farthestRatDistance ){
					farthestRatDistance = distance(current, rats[i]);
					farthestRatIndex = i;
				}
			}	
			
			for (int i = 0; i < predictionLookAhead; i++) {
				if (farthestRatDistance <  10+i*pspeed) {
					Point predictedRatPosition = predictedRatPositions.get(farthestRatIndex).get(i);
					return predictedRatPosition;
				}
			}
			return rats[farthestRatIndex];
		}
		else {
		
			// Sequentially check one future tick at a time; as soon as a rat is
			// inside the future tick circle, choose that rat.
			for (int i = 0; i < predictionLookAhead; i++) {
				for (int j = 0; j < rats.length; j++) {
					if (ratsFound[j] == true) {
						continue;
					}
					// Find where the j'th rat will be at future time i
					Point predictedPoint = predictedRatPositions.get(j).get(i);
					double ratDist = distance(current, predictedPoint);
					if ((ratDist > 10) && (ratDist < (10 + i * mpspeed))) {
	//					System.out.println("returned at i = " + i);
	//					System.out.println("ratDist = " + ratDist);
						return predictedPoint;
					}
				}
			}
	//		System.out.println("findClosestRatNotInInfluence should not get to this point!  Here's what ratsFound looks like:");
	//		System.out.println("ratsFound: " + Arrays.toString(ratsFound));
			
			// If we don't have enough heap space to store enough future positions
			// so that we intersect with a rat, just find the closest rat at the current time and
			// head there.
			double closestSoFar = Integer.MAX_VALUE;
			Point closestRat = new Point();
			// Assumed true until we find a rat not in influence
			for(int i = 0; i < rats.length; i++) {
				if (getSide(rats[i]) == 0 || ratsFound[i] == true) {
					continue;
				}
				double ratDist = distance(current, rats[i]);
				if (ratDist < closestSoFar && ratDist > 10) {
					closestSoFar = ratDist;
					closestRat = rats[i];
				}
			}
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
	
	boolean isInfluencingRats(Point current, Point[] rats) {
		for(int i = 0; i < rats.length; i++) {
			if( distance(current, rats[i]) < 10.0 ) {
				return true;
			}
		}
		return false;
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