package piedpipers.sim;

// general utilities
import java.io.*;
import java.util.List;
import java.util.*;

import javax.tools.*;

import java.util.concurrent.*;
import java.net.URL;

// gui utility
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import javax.swing.*;

enum PType {
	PTYPE_MUSICPIPERS, PTYPE_PIPERS, PTYPE_RAT
}

public class Piedpipers {
	static String ROOT_DIR = "piedpipers";

	// recompile .class file?
	static boolean recompile = true;

	// print more details?
	static boolean verbose = true;

	// Step by step trace
	static boolean trace = true;

	// enable gui
	static boolean gui = true;

	// default parameters
	static int DEFAULT_PIPERS = 1;
	static int DEFAULT_RATS = 10;

	static double WALK_DIST = 10.0; // <10m, rats walk with music piper
	static double STOP_DIST = 2.0; // <2m, rats stop

	static double WALK_SPEED = 0.1; // 1m/s, walking speed for rats
	static double MUSICPIPER_SPEED = 0.1; // 1m/s, walking speed for music piper
	static double PIPER_SPEED = 0.5; // 5m/s, walking speed for no music piper

	static double OPEN_LEFT; // left side of center opening
	static double OPEN_RIGHT; // right side of center opening

	static int MAX_TICKS = 100000;
	static int seed;
	static Random random;
	public static int[] thetas;

	// list files below a certain directory
	// can filter those having a specific extension constraint
	//
	static List<File> directoryFiles(String path, String extension) {
		List<File> allFiles = new ArrayList<File>();
		allFiles.add(new File(path));
		int index = 0;
		while (index != allFiles.size()) {
			File currentFile = allFiles.get(index);
			if (currentFile.isDirectory()) {
				allFiles.remove(index);
				for (File newFile : currentFile.listFiles())
					allFiles.add(newFile);
			} else if (!currentFile.getPath().endsWith(extension))
				allFiles.remove(index);
			else
				index++;
		}
		return allFiles;
	}

	// compile and load players dynamically
	//
	static Player loadPlayer(String group) {
		try {
			// get tools
			URL url = Piedpipers.class.getProtectionDomain().getCodeSource()
					.getLocation();
			// use the customized reloader, ensure clearing all static
			// information
			ClassLoader loader = new ClassReloader(url,
					Piedpipers.class.getClassLoader());
			if (loader == null)
				throw new Exception("Cannot load class loader");
			JavaCompiler compiler = null;
			StandardJavaFileManager fileManager = null;
			// get separator
			String sep = File.separator;
			// load players
			// search for compiled files
			File classFile = new File(ROOT_DIR + sep + group + sep
					+ "Player.class");
			System.err.println(classFile.getAbsolutePath());
			if (!classFile.exists() || recompile) {
				// delete all class files
				List<File> classFiles = directoryFiles(ROOT_DIR + sep + group,
						".class");
				System.err.print("Deleting " + classFiles.size()
						+ " class files...   ");
				for (File file : classFiles)
					file.delete();
				System.err.println("OK");
				if (compiler == null)
					compiler = ToolProvider.getSystemJavaCompiler();
				if (compiler == null)
					throw new Exception("Cannot load compiler");
				if (fileManager == null)
					fileManager = compiler.getStandardFileManager(null, null,
							null);
				if (fileManager == null)
					throw new Exception("Cannot load file manager");
				// compile all files
				List<File> javaFiles = directoryFiles(ROOT_DIR + sep + group,
						".java");
				System.err.print("Compiling " + javaFiles.size()
						+ " source files...   ");
				Iterable<? extends JavaFileObject> units = fileManager
						.getJavaFileObjectsFromFiles(javaFiles);
				boolean ok = compiler.getTask(null, fileManager, null, null,
						null, units).call();
				if (!ok)
					throw new Exception("Compile error");
				System.err.println("OK");
			}
			// load class
			System.err.print("Loading player class...   ");
			Class playerClass = loader.loadClass(ROOT_DIR + "." + group
					+ ".Player");
			System.err.println("OK");
			// set name of player and append on list
			Player player = (Player) playerClass.newInstance();
			if (player == null)
				throw new Exception("Load error");
			else
				return player;

		} catch (Exception e) {
			e.printStackTrace(System.err);
			return null;
		}

	}

	static Player[] loadPlayers(String group, int npipers, int d) {
		Player[] players = new Player[npipers];
		for (int i = 0; i < npipers; ++i) {
			Player p = loadPlayer(group);
			p.id = i; // set the piper id
			p.dimension = d;
			players[i] = p;
		}
		return players;
	}

	// generate a random position on the given side
	static Point randomPosition(int side) {
		Point point = new Point();
		// generate [0-50)
		//random.setSeed(20);//seed
		point.x = random.nextDouble() * dimension * 0.5;
		// generate [50-100)
		if (side == 1)
			point.x = point.x + (dimension * 0.5);
		// generate [0-100)
		point.y = random.nextDouble() * dimension;
		return point;
	}

	// compute Euclidean distance between two points
	static double distance(Point a, Point b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
	}

	static double vectorLength(double ox, double oy) {
		return Math.sqrt(ox * ox + oy * oy);
	}

	void playgui() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				PiedPipersUI ui = new PiedPipersUI();
				ui.createAndShowGUI();
			}
		});
	}

	class PiedPipersUI extends JPanel implements ActionListener {
		int FRAME_SIZE = 800;
		int FIELD_SIZE = 600;
		boolean playing = false;
		JFrame f;
		FieldPanel field;
		JButton next;
		JButton next10;
		JButton next30;
		JButton next50;
		JButton next100;
		JButton playbtn;
		JLabel label;
		javax.swing.Timer timer;

		public PiedPipersUI() {
			setPreferredSize(new Dimension(FRAME_SIZE, FRAME_SIZE));
			setOpaque(false);
		}

		public void init() {
		}

		private boolean performOnce() {
            label.setText("Ticks: "+tick);
			if (tick > MAX_TICKS) {
				label.setText("Time out!!! The player lured " + countCapturedRats() + " to their doom!");
				
				// print error message
				System.err.println("[ERROR] The player is time out!");
				System.err.println("The player managed to lure " + countCapturedRats() + " to their doom!");
				next.setEnabled(false);
				return false;
			} else if (endOfGame()) {
				label.setText("Finishes in " + tick + " ticks!");
				// print success message
				System.err.println("[SUCCESS] The player achieves the goal in "
						+ tick + " ticks.");
				next.setEnabled(false);
				return false;
			} else {
				playStep();
				return true;
			}
		}

		public void actionPerformed(ActionEvent e) {
			int steps = 0;

			if (e.getSource() == timer)
				steps = 10;
			else if (e.getSource() == next)
				steps = 1;
			else if (e.getSource() == next10)
				steps = 10;
			else if (e.getSource() == next30)
				steps = 30;
			else if (e.getSource() == next50)
				steps = 50;
			else if (e.getSource() == next100)
				steps = 100;
			else if (e.getSource() == playbtn) {
				if (playing) {
					playbtn.setText("Play");
					playing = false;
					timer.stop();
				} else {
					playbtn.setText("Stop");
					playing = true;
					timer = new javax.swing.Timer(10, this);
					timer.start();
				}
			}

			for (int i = 0; i < steps; ++i) {
				if (!performOnce())
					break;
			}

			repaint();
		}

		public void createAndShowGUI() {
			this.setLayout(null);

			f = new JFrame("PiedPipers");
			field = new FieldPanel(1.0 * FIELD_SIZE / dimension);
			next = new JButton("Next");
			next.addActionListener(this);
			next.setBounds(0, 0, 100, 50);
			next10 = new JButton("Next10");
			next10.addActionListener(this);
			next10.setBounds(100, 0, 100, 50);
			next30 = new JButton("Next30");
			next30.addActionListener(this);
			next30.setBounds(200, 0, 100, 50);
			next50 = new JButton("Next50");
			next50.addActionListener(this);
			next50.setBounds(300, 0, 100, 50);
			next100 = new JButton("Next100");
			next100.addActionListener(this);
			next100.setBounds(400, 0, 100, 50);
			playbtn = new JButton("Play");
			playbtn.addActionListener(this);
			playbtn.setBounds(500, 0, 100, 50);

			label = new JLabel();
			label.setBounds(0, 60, 500, 50);
			label.setFont(new Font("Arial", Font.PLAIN, 15));
			label.setText("");

			field.setBounds(100, 100, FIELD_SIZE + 50, FIELD_SIZE + 50);

			this.add(next);
			this.add(next10);
			this.add(next30);
			this.add(next50);
			this.add(next100);
			this.add(playbtn);
			this.add(label);
			this.add(field);

			f.add(this);

			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.pack();
			f.setVisible(true);
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
		}

	}

	class FieldPanel extends JPanel {
		double PSIZE = 10;
		double s;
		BasicStroke stroke = new BasicStroke(2.0f);
		double ox = 10.0;
		double oy = 10.0;

		public FieldPanel(double scale) {
			setOpaque(false);
			s = scale;
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2 = (Graphics2D) g;
			g2.setStroke(stroke);

			// draw 2D rectangle
			g2.draw(new Rectangle2D.Double(ox, oy, dimension * s, dimension * s));

			// draw 2D line
			g2.draw(new Line2D.Double(0.5 * dimension * s + ox, 0 + oy, 0.5
					* dimension * s + ox, OPEN_LEFT * s + oy));

			g2.draw(new Line2D.Double(0.5 * dimension * s + ox, OPEN_RIGHT * s
					+ oy, 0.5 * dimension * s + ox, dimension * s + oy));
			// draw rats
						drawRats(g2);
			// draw pipers
			drawPipers(g2);

			
		}

		public void drawPoint(Graphics2D g2, Point p, PType type) {
			if (type == PType.PTYPE_MUSICPIPERS) {
				g2.setPaint(Color.BLUE);
				Ellipse2D e = new Ellipse2D.Double(p.x * s - PSIZE/2  + ox, p.y
						* s - PSIZE/2  + oy, PSIZE, PSIZE);
				g2.setStroke(stroke);
				g2.draw(e);
				g2.fill(e);
			}
			else if (type == PType.PTYPE_PIPERS) {
				g2.setPaint(Color.ORANGE);
				Ellipse2D e = new Ellipse2D.Double(p.x * s - PSIZE/2  + ox, p.y
						* s - PSIZE/2  + oy, PSIZE, PSIZE);
				g2.setStroke(stroke);
				g2.draw(e);
				g2.fill(e);
			}
			else {
				g2.setPaint(Color.BLACK);

			Ellipse2D e = new Ellipse2D.Double(p.x * s - PSIZE / 2 + ox, p.y
					* s - PSIZE / 2 + oy, PSIZE, PSIZE);
			g2.setStroke(stroke);
			g2.draw(e);
			g2.fill(e);
			}
			}
		public void drawCircle(Graphics2D g2, Point p, PType type) {
			Ellipse2D eOuter = new Ellipse2D.Double(p.x * s - (WALK_DIST * s) / 2 + ox, p.y
					* s - (WALK_DIST * s) / 2 + oy, WALK_DIST * s, WALK_DIST * s);
			Ellipse2D eInner = new Ellipse2D.Double(p.x * s - (STOP_DIST * s) / 2 + ox, p.y
					* s - (STOP_DIST * s) / 2 + oy, STOP_DIST * s, STOP_DIST * s);
			g2.setStroke(stroke);
			g2.setPaint(Color.BLUE);
			g2.draw(eOuter);
			g2.setPaint(Color.RED);
			g2.draw(eInner);
		}
		
		public void drawPipers(Graphics2D g2) {
			for (int i = 0; i < npipers; ++i) {
				if (players[i].music) {
					drawPoint(g2, pipers[i], PType.PTYPE_MUSICPIPERS);
					drawCircle(g2, pipers[i], PType.PTYPE_MUSICPIPERS);
				} else {
					drawPoint(g2, pipers[i], PType.PTYPE_PIPERS);
				}
			}
		}

		public void drawRats(Graphics2D g2) {
			for (int i = 0; i < nrats; ++i) {
				drawPoint(g2, rats[i], PType.PTYPE_RAT);
			}
		}
	}

	Point getClosestMusicPiper(int ratId) {
		int minpiper = -1;
		double mindist = Double.MAX_VALUE;
		for (int i = 0; i < npipers; ++i) {
			if (players[i].music) {
				double d = distance(rats[ratId], pipers[i]);
				if (d < mindist && d > 0) { // ignore overlapping pipers?
					mindist = d;
					minpiper = i;
				}
			}
		}
		return pipers[minpiper];
	}

	Point moveRat(int ratId) {
		Point thisRat = rats[ratId];
		double rspeed = 0;
		boolean anymusic = false;
		boolean randommove = true;
		double ox = 0, oy = 0;
		// detect whether there is any piper playing music
		for (int i = 0; i < npipers; i++) {
			if (players[i].music) {
				anymusic = true;
			}
		}

		if (anymusic) {
			Point closestPiper = getClosestMusicPiper(ratId);
			double dist = distance(thisRat, closestPiper);
			assert dist > 0;
			// within 2 meters of a music piper, stop there
			if (dist < STOP_DIST) {
				rspeed = 0;
				randommove = false;
				//Random random = new Random();
				int theta = random.nextInt(360);
				thetas[ratId] = theta;

			} else if (dist < WALK_DIST) {
				// between 2-10 meters of a music piper, move towards the piper
				rspeed = WALK_SPEED;
				randommove = false;
				//Random random = new Random();
				int theta = random.nextInt(360);
				thetas[ratId] = theta;
			}
			// if (dist<10) {
			ox = (closestPiper.x - thisRat.x) / dist * rspeed;
			oy = (closestPiper.y - thisRat.y) / dist * rspeed;
			// }
		}
		// non of above cases, just random wandering as rats
		if (randommove) {
			//Random random = new Random();
			//int theta = random.nextInt(360);
			ox = WALK_SPEED * Math.sin(thetas[ratId] * Math.PI / 180);
			oy = WALK_SPEED * Math.cos(thetas[ratId] * Math.PI / 180);
			// System.out.printf("(ox, oy)=(%f, %f)\n", ox, oy);
		}
		Point npos = updatePosition(thisRat, ox, oy, ratId);
		return npos;
	}

	// update the current point according to the offsets
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
			int theta = -thetas[rat];
			thetas[rat] = theta;
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
			
			int theta = -thetas[rat];
			thetas[rat] = theta;
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
		
			int theta = 180-thetas[rat];
			thetas[rat] = theta;
			return updatePosition(temp, ox, oy2, id_rat);
		}
		// hit the bottom fence
		if (ny > dimension) {
			// System.err.println("RAT HITS THE BOTTOM FENCE!!!");
			Point temp = new Point(now.x, dimension);
			double moved = (dimension - now.y);
			double oy2 = -(oy - moved);
			//Random random = new Random();
			int theta = 180-thetas[rat];
			thetas[rat] = theta;
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
			int theta = -thetas[rat];
			thetas[rat] = theta;
			return updatePosition(temp, ox2, oy, id_rat);
		}
		// otherwise, we are good
		return new Point(nx, ny);
	}

	// up side is 0
	// bottom side is 1
	// at the fence 2
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
			System.out.printf("hit the medium fence");
			return true;
		}
	}

	void moveRats() {
		// move every rat
		Point[] newRats = new Point[nrats];
		for (int i = 0; i < nrats; ++i) {
			// compute its velocity vector
			newRats[i] = moveRat(i);
		}
		rats = newRats;
	}

	boolean validateMove(Point src, Point dst, int id) {
		if (dst.x < 0 || dst.x > dimension)
			return false;
		if (dst.y < 0 || dst.y > dimension)
			return false;
		if (players[id].music) {
			if (distance(src, dst) > MUSICPIPER_SPEED)
				return false;
		} else {
			if (distance(src, dst) > PIPER_SPEED)
				return false;
		}
		if (hitTheFence(src.x, src.y, dst.x, dst.y))
			return false;
		return true;
	}

	// detect whether the player has achieved the requirement
	boolean endOfGame() {
		for (int i = 0; i < nrats; ++i) {
			if (getSide(rats[i]) == 1)
				return false;
		}
		return true;
	}

	static Point[] copyPointArray(Point[] points) {
		Point[] npoints = new Point[points.length];
		for (int p = 0; p < points.length; ++p)
			npoints[p] = new Point(points[p]);

		return npoints;
	}

	void playStep() {
		tick++;

		// move the player dogs
		Point[] next = new Point[npipers];
		for (int d = 0; d < npipers; ++d) {
			Point[] pipercopy = copyPointArray(pipers);

			try {
				next[d] = players[d].move(pipercopy, rats, pipermusic, thetas);
				pipermusic[d] = players[d].music;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("[ERROR] Player throws exception!!!!");
				next[d] = pipers[d]; // let the dog stay
			}

			if (verbose) {
				System.err.format(
						"Piper %d moves from (%.2f,%.2f) to (%.2f,%.2f)\n",
						d + 1, pipers[d].x, pipers[d].y, next[d].x, next[d].y);
			}

			// validate player move
			if (!validateMove(pipers[d], next[d], d)) {
				System.err.println("[ERROR] Invalid move, let the piper stay.");
				// for testing purpose
				// let's make the dog stay
				next[d] = pipers[d];
			}
		}

		// move sheeps
		moveRats();

		// move dogs
		pipers = next;
	}

	void play() {
		while (tick <= MAX_TICKS) {
			if (endOfGame())
				break;
			playStep();
		}

		if (tick > MAX_TICKS) {
			// Time out
			System.err.println("[ERROR] The player is time out!");
		} else {
			// Achieve the goal
			System.err.println("[SUCCESS] The player achieves the goal in "
					+ tick + " ticks.");
		}
	}

	int countCapturedRats(){
		int capturedRats = 0;
		for (int i = 0; i < nrats; ++i) {
			if (getSide(rats[i]) != 1)
				capturedRats++;
		}
		return capturedRats;
	}
	
	void init() {
		// initialize rats
		rats = new Point[nrats];
		thetas = new int[nrats];
		pipermusic = new boolean[npipers];
		for (int s = 0; s < nrats; ++s)
			rats[s] = randomPosition(1);

		// initialize pipers
		pipers = new Point[npipers];
		for (int d = 1; d <= npipers; ++d) {
			double x = 0;
			double y = 1.0 * d / (npipers + 1) * dimension;
			pipers[d - 1] = new Point(x, y);
		}

		for (int d = 0; d < npipers; ++d) {
			players[d].init();
			pipermusic[d] = false;
			
		}
		for (int i=0; i< nrats; i++) {
			//Random random = new Random();
			//random.setSeed(20);//set seed
			int theta = random.nextInt(360);
			thetas[i]=theta;
		}
	}

	Piedpipers(Player[] players, int nrats) {
		this.players = players;
		this.npipers = players.length;
		this.nrats = nrats;

		// print config
		System.err.println("##### Game config #####");
		System.err.println("Pipers: " + players.length);
		System.err.println("Rats: " + nrats);
		// System.err.println("Blacks: " + nblacks);
		// System.err.println("Mode: " + mode);
		System.err.println("##### end of config #####");
	}

	public static void main(String[] args) throws Exception {
		// game parameters
		String group = null;
		int npipers = DEFAULT_PIPERS; // d
		int nrats = DEFAULT_RATS; // S

		if (args.length > 0)
			group = args[0];
		if (args.length > 1)
			npipers = Integer.parseInt(args[1]);
		if (args.length > 2)
			nrats = Integer.parseInt(args[2]);
		if (args.length > 3)
			gui = Boolean.parseBoolean(args[3]);
		if (args.length > 4)
			seed = Integer.parseInt(args[4]);
		if (args.length >5)
			dimension = Integer.parseInt(args[5]);
		random = new Random(seed);
		OPEN_LEFT = dimension/2-1;
		OPEN_RIGHT = dimension/2+1;
		System.out.printf("the open left and open right are %f and %f", OPEN_LEFT, OPEN_RIGHT);
		// load players
		Player[] players = loadPlayers(group, npipers, dimension);

		// create game
		Piedpipers game = new Piedpipers(players, nrats);
		// init game
		game.init();
		// play game
		if (gui) {
			game.playgui();
		} else {
			game.play();
		}

	}

	// players
	Player[] players;
	// dog positions
	Point[] pipers;
	// sheep positions
	Point[] rats;
	boolean[] pipermusic;

	// game config
	int npipers;
	static int nrats;
	// int nblacks;
	// boolean mode;

	int tick = 0;

	static int dimension; // dimension of the map
}
