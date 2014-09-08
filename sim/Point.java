package piedpipers.sim;

public class Point {
	public double x;
	public double y;

	public Point() {
		x = 0;
		y = 0;
	}

	public Point(double xx, double yy) {
		x = xx;
		y = yy;
	}

	public Point(Point o) {
		this.x = o.x;
		this.y = o.y;
	}

	public boolean equals(Point o) {
		return o.x == x && o.y == y;
	}
}