package piedpipers.group6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import piedpipers.sim.Point;
import piedpipers.group6redirect.RedirectPlayer;

public class Player extends piedpipers.sim.Player {
	static RedirectPlayer rPlayer;
	static int npipers;
	
	static double pspeed = 0.4999999999;
	static double mpspeed = 0.099999999;
	static double WALK_SPEED = 0.1; // 1m/s, walking speed for rats
	
	static int predictionLookAhead = 2000;
	
	// Used to wait for 5 ticks before moving into the left side with all the rats.
	// Helps make sure there are no stragglers.
	static int catchUpCounter;
	
	// Used to make sure all pipers get across fence initially in sweep.
	static boolean hitInsidePointComing = false;
	static boolean hitInsidePointGoing = false;
	
	static Point finalDropOffPoint; // On the left side, where everyone should bring the rats when they're done.
	static Point gate; // The exact middle of the gate.
	static Point insidePoint; // A point 10m inside the gate on the right side, used as intermediary so pipers
	// don't try to go through the wall or lose rats.
	
	static boolean openedUpField = false;
	
	// Maps each piper's id to its dedicated section of the board
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
	static boolean hitBottomWall = false;
	
	boolean[] ratsFound;
	
	static Point targetRat;
	
	public void init() {

		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		
		finalDropOffPoint = new Point(dimension / 2 - 10, dimension / 2); // On the left side, where everyone should bring the rats when they're done.
		gate = new Point(dimension/2, dimension/2); // The exact middle of the gate.
		insidePoint = new Point(dimension / 2 + 10, dimension / 2); // A point inside the gate on the right side, used as intermediary so pipers
		// don't try to go through the wall. DO NOT make this more than 10; it wont work on smaller graphs.
		catchUpCounter = 5;
		
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
			// All initializations that require information.
			npipers = pipers.length;
			for (int i = 0; i < rats.length; i++) {
				predictedRatPositions.add(new ArrayList<Point>(predictionLookAhead));
			}
			rPlayer = new RedirectPlayer();
			rPlayer.id = id;
			rPlayer.dimension = dimension;
			ratsFound = new boolean[rats.length];
			initi = true;
		}
		
		//Point next = rPlayer.move(pipers, rats, pipermusic, thetas);
		//this.music = rPlayer.music;
		//return next;
		
		// LEVEL 1 OF DECISION TREE
		if (pipers.length == 1) {
			return commencePredictiveGreedySearch(pipers, rats, pipermusic, thetas);
		}
		
		// LEVEL 2
		// if #remaining rats * 10 / dimension < 4, start greedy-searching.
		// 4 is an experimental value. While dimension^2 makes more sense, it ended up
		// being too unwieldy to attempt to calibrate, hence the change to single dimension.
		if (((rats.length - numRatsFound(ratsFound)) / ( dimension / 10)) < 4) {
			//System.out.println((rats.length - numRatsFound(ratsFound) / (dimension * dimension)) < 100);
			// LEVEL 3
			// TODO: 'high enough' is a magic number
			// if piper to board density is high enough, use predictive greedy with partitioning.
			//System.out.println("222");
			return commencePredictiveGreedySearch(pipers, rats, pipermusic, thetas);
			//return next;
			// else use redirection.
			//return rPlayer.move(pipers, rats, pipermusic, thetas);
		}
		else {
			// LEVEL 3
			// TODO: 40 is a magic number - it's how we could line up horizontally
			// and have approximately half the board size covered by pipers.
			// magic number:
			// should never be below 40 (that's when the board size is guaranteed to be
			// swept through.
			// 45 is just an experimental value that made sense.
			// it might end up higher since commencePredictiveGreedySearch is buggy when
			// switched to after sweep finishes.
			if (dimension / pipers.length < 45) {
				//System.out.println("333333");
				return commenceSweep(pipers, rats, pipermusic, thetas);
			}
			else {
				//System.out.println("4444444444444");
				return commencePredictiveGreedySearch(pipers, rats, pipermusic, thetas);
			}
		}
	}
	
	Point commenceSweep(Point[] pipers, Point[] rats, boolean[] pipermusic, int[] thetas) {
		String mode = "sweep";
		ratThetas = thetas.clone();
		lastRatThetas = currentRatThetas;
		currentRatThetas = thetas.clone();
		
		Point current = pipers[id];
		Point gate = new Point(dimension/2, dimension/2);
		double ox = 0; // delta x/y
		double oy = 0;
		Point targetPoint = new Point();
		double dist = 0;
		switch (getSide(current)) {
		case 0:
			if (finishedRound) {
				// The round is finished. In sweep, this will rarely happen because
				// its intention is to just reduce the density of rats. However, in
				// the case where dimension / pipers <=20, it can grab all the rats
				// in one sweep and retreat to the dropoff point without going into any
				// other stage.

				if (!hitInsidePointGoing) {
					// Go to the inside point first.
					targetPoint = insidePoint;
					dist = distance(current, targetPoint);
					// Update hitInsidePoint
					if (dist < 1) {
						hitInsidePointGoing = true;
					}
				}
				else {
					// You've hit the inside point.
					// Wait until the straggler rats catch up.
					// Now take them to the final dropoff point.
					targetPoint = finalDropOffPoint;
					dist = distance(current, targetPoint);
				}
				this.music = true;
				ox = (targetPoint.x - current.x) / dist * mpspeed;
				oy = (targetPoint.y - current.y) / dist * mpspeed;
//					System.out.println("move toward dropoff point");	
			} // End finishedRound case.
			else {
				// You haven't begun collecting rats yet.
				this.music = false;
				dist = distance(current, gate);
				assert dist > 0;
				ox = (gate.x - current.x) / dist * pspeed;
				oy = (gate.y - current.y) / dist * pspeed;
//				System.out.println("move toward the right side");
			}
		break;
		default:
			// You're on the right side.
			if (boundaries.isEmpty()) {
				setBoundaries(pipers, mode);
			}
			// If you haven't hit the top wall yet, head there.
			if (!hitTheWall){
				hitTheWall = closeToWall(current);
				this.music = false;
				if (hitInsidePointComing) {
					targetPoint = getPiperStartPoint(id, mode);
				}
				else {
					targetPoint = insidePoint;
					if (distance(current, targetPoint) < 1) {
						hitInsidePointComing = true;
					}
				}
				dist = distance(current, targetPoint);
				ox = (targetPoint.x - current.x) / dist * pspeed;
				oy = (targetPoint.y - current.y) / dist * pspeed;
			}
			else {
				// You've hit the top wall. Start moving downwards.
				// If there are enough pipers, just move straight.
				// If not enough pipers, use greedy search within own section.
				this.music = true;
				if (dimension / pipers.length <= 20) {
					if (!hitBottomWall) {
						targetPoint = getPiperEndPoint(id, mode);
						dist = distance(current, targetPoint);
						if (dist < 1) {
							// You've hit the bottom wall.
							hitBottomWall = true;
							finishedRound = true;
						}
					}
					else {
						// You've hit the bottom wall. Take all the rats back to the gate.
						if (!hitInsidePointGoing) {
							targetPoint = insidePoint;
							dist = distance(current, targetPoint);
							if (dist < 1) {
								hitInsidePointGoing = true;
							}
						}
						else {
							// You've hit the inside point.
							// Wait until the straggler rats catch up.
							
							// Now take them to the final dropoff point.
							targetPoint = finalDropOffPoint;
							dist = distance(current, finalDropOffPoint);	
						} // End hit inside point.
							
					} // End hit bottom wall.
					ox = (targetPoint.x - current.x) / dist * mpspeed;
					oy = (targetPoint.y - current.y) / dist * mpspeed;	
				}
				else {
					// You don't have enough pipers to guarantee a clean sweep.
					// Use sweep with greedy search within your vertical section.
					
					// Find closest rat. Move in that direction.
					Point closestRat = findClosestRat("vertical", current, rats, pipers);
					if (!isValidTarget(closestRat)) {
						
						if (!openedUpField) {
							// Make the piper's purview the entire field to see if that changes things
							int[] entireHorizField = {dimension/2, dimension};
							boundaries.put(new Integer(id), entireHorizField);
							openedUpField = true;
						}
						else {
							// All rats have been found. Move back toward gate.
							finishedRound = true;
							this.music = true;
							
							dist = distance(current, insidePoint);
							if (!hitInsidePointGoing) {
								// Go to the inside point first.
								targetPoint = insidePoint;
								dist = distance(current, targetPoint);
								ox = (targetPoint.x - current.x) / dist * mpspeed;
								oy = (targetPoint.y - current.y) / dist * mpspeed;
								// Update hitInsidePoint
								if (dist < 1) {
									hitInsidePointGoing = true;
								}
							}
							else {
								// You've hit the inside point.
								// Wait until the straggler rats catch up.
								// Now take them to the final dropoff point.
								targetPoint = finalDropOffPoint;
								dist = distance(current, targetPoint);
								ox = (targetPoint.x - current.x) / dist * mpspeed;
								oy = (targetPoint.y - current.y) / dist * mpspeed;
							}
						}
					}
					else {
						// All Rats have not been found; continue to catch em.
						dist = distance(current, closestRat);
						
						if( isInfluencingRats(current, rats) ) {
							this.music = true;
							ox = (closestRat.x - current.x) / dist * mpspeed;
							oy = (closestRat.y - current.y) / dist * mpspeed;
						} else {
							this.music = false;
							ox = (closestRat.x - current.x) / dist * pspeed;
							oy = (closestRat.y - current.y) / dist * pspeed;
						}
	//					System.out.println("moved toward closest rat at " +
	//							closestRat.x + ", " + closestRat.y);
					}
				}// End greedy search option.
			} // End hit the wall option
		}
		current.x += ox;
		current.y += oy;
		return current;
	}
		
	Point commencePredictiveGreedySearch(Point[] pipers, Point[] rats, boolean[] pipermusic, int[] thetas) {
		String mode = "gp";
		ratThetas = thetas.clone();
		lastRatThetas = currentRatThetas;
		currentRatThetas = thetas.clone();
		
		Point current = pipers[id];
		Point gate = new Point(dimension/2, dimension/2);
		double ox = 0; // delta x/y
		double oy = 0;
		Point targetPoint = new Point();
		double dist = 0;
		
		switch (getSide(current)) {
		case 0:
			if (finishedRound) {
				// You're on the left side. Proceed to final drop off point.
				targetPoint = finalDropOffPoint;
				dist = distance(current, targetPoint);
				this.music = true;
				ox = (targetPoint.x - current.x) / dist * mpspeed;
				oy = (targetPoint.y - current.y) / dist * mpspeed;
//						System.out.println("move toward dropoff point");			
			} // End finishedRound case.
			
			else {
				// You haven't begun collecting rats yet.
				this.music = false;
				dist = distance(current, gate);
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
				setBoundaries(pipers, mode); // greedy partitioned
			}
			// You've just entered the right hand side.
			// Send pipers to the farthest edge of in their respective section until they hit the wall
			if (!hitTheWall) {
					hitTheWall = closeToWall(current);
					this.music = false;
					Point piperStartPoint = getPiperStartPoint(id, mode); // gp = greedy predictive
					dist = distance(current, piperStartPoint);
					ox = (piperStartPoint.x - current.x) / dist * pspeed;
					oy = (piperStartPoint.y - current.y) / dist * pspeed;
			}
			else {
				// Find closest rat. Move in that direction.
				Point closestRat = findClosestRat("horizontal", current, rats, pipers);
				if (!isValidTarget(closestRat)) {
					// Could be: no more rats in your section OR no more rats at all.
					
					if (!openedUpField) {
						// Make the piper's purview the entire field to see if that changes things
						int[] entireHorizField = {dimension/2, dimension};
						boundaries.put(new Integer(id), entireHorizField);
						openedUpField = true;
					}
					else {
						// All rats have been found. Move back toward gate.
						finishedRound = true;
						this.music = true;
						
						dist = distance(current, insidePoint);
						if (!hitInsidePointGoing) {
							// Go to the inside point first.
							targetPoint = insidePoint;
							dist = distance(current, targetPoint);
							ox = (targetPoint.x - current.x) / dist * mpspeed;
							oy = (targetPoint.y - current.y) / dist * mpspeed;
							// Update hitInsidePoint
							if (dist < 1) {
								hitInsidePointGoing = true;
							}
						}
						else {
							// You've hit the inside point.
							// Wait until the straggler rats catch up.
							// Now take them to the final dropoff point.
							targetPoint = finalDropOffPoint;
							dist = distance(current, targetPoint);
							ox = (targetPoint.x - current.x) / dist * mpspeed;
							oy = (targetPoint.y - current.y) / dist * mpspeed;
						}
					}	
				}
				else {
					// All Rats have not been found; continue to catch em.
					dist = distance(current, closestRat);
					
					if( isInfluencingRats(current, rats) ) {
						this.music = true;
						ox = (closestRat.x - current.x) / dist * mpspeed;
						oy = (closestRat.y - current.y) / dist * mpspeed;
					} else {
						this.music = false;
						ox = (closestRat.x - current.x) / dist * pspeed;
						oy = (closestRat.y - current.y) / dist * pspeed;
					}
				}
			}
		}
		current.x += ox;
		current.y += oy;
		return current;
	}
	
	void setBoundaries(Point[] pipers, String mode) {
		if (mode.equals("gp")) {
			for (int i = 0; i < pipers.length; i++) {
				int topYVal = i * dimension / npipers;
				int bottomYVal = topYVal + (dimension/npipers) - 1;
				int[] section = {topYVal, bottomYVal};
				// Make the ID an Integer so it can be used as a key in a HashMap
				boundaries.put(new Integer(i), section);
			}
		}
		else if (mode.equals("sweep")) {
			int width = dimension / 2;
			for (int i = 0; i < pipers.length; i++) {
				int leftXVal = width + i * width / npipers;
				int rightXVal = leftXVal + (width / npipers) - 1;
				int[] section = {leftXVal, rightXVal};
				// Make the ID an Integer so it can be used as a key in a HashMap
				boundaries.put(new Integer(i), section);
			}
		}
	}
	
	/**
	 * Sweep, PG (predictive/partitioned greedy)
	 * Finds each piper's place along the initial wall, depending on mode.
	 * @param id
	 * @param mode
	 * @return
	 */
	Point getPiperStartPoint(int id, String mode) {
		int[] boundary = boundaries.get(id);
		if (mode.equals("gp")) {
			int startX = dimension;
			int startY = boundary[0] + (boundary[1] - boundary[0]) / 2;
			return new Point(startX, startY);
		}
		else {
			//if (mode.equals("sweep")) {
			int startX = boundary[0] + (boundary[1] - boundary[0]) / 2;
			int startY = 0;
			return new Point(startX, startY);
		}	
	}
	
	Point getPiperEndPoint(int id, String mode) {
		int[] boundary = boundaries.get(id);
		//if (mode.equals("sweep")) {
			int endX = boundary[0] + (boundary[1] - boundary[0]) / 2;
			int endY = dimension;
			return new Point(endX, endY);
	}
	
	void resetCatchUpCounter() {
		catchUpCounter = 5;
	}
	
	boolean isValidTarget(Point rat) {
		if (rat == null || getSide(rat) == 0) {
			return false;
		}
		return true;
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
	
	Point findClosestRat(String orientation, Point current, Point[] rats, Point[] pipers) {
		
		// First, generate list of rats that have already been found.
		// If they've been found, don't generate future position.
		ratsFound = new boolean[rats.length];
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
		
		// X == close
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
					if (isInSection(orientation, predictedPoint, boundaries.get(id))) {
						//System.out.println("returned at i = " + i);
						//System.out.println("ratDist = " + ratDist);
						return predictedPoint;
					}	
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
				if (isInSection(orientation, rats[i], boundaries.get(id))) {
					closestSoFar = ratDist;
					closestRat = rats[i];
				}	
			}
		}
		return closestRat;
	}
	
	boolean isInSection(String orientation, Point current, int[] section) {
		if (orientation.equals("vertical")) {
			int leftCoord = section[0];
			int rightCoord = section[1];
			if (current.x > leftCoord && current.x < rightCoord) {
				return true;
			}
		}
		else {
			int topCoord = section[0];
			int bottomCoord = section[1];
			if (current.y > topCoord && current.y < bottomCoord) {
				return true;
			}
		}
		return false;
	}
	
	boolean isInfluencingRats(Point current, Point[] rats) {
		for(int i = 0; i < rats.length; i++) {
			if( distance(current, rats[i]) < 10.0 ) {
				return true;
			}
		}
		return false;
	}
	
	int numRatsFound(boolean[] ratsFound) {
		int numRats = 0;
		for (boolean b : ratsFound) {
			if (b) {
				numRats++;
			}
		}
		return numRats;
	}
	
	boolean closeToWall (Point current) {
		boolean wall = false;
		if (Math.abs(current.x-dimension)<pspeed) {
			wall = true;
		}
		if (Math.abs(current.y-dimension)<pspeed) {
			wall = true;
		}
		// Top wall
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
