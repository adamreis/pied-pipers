package piedpipers.sim;

import java.net.URL;
import java.net.URLClassLoader;

// Customized reloader - Force to reload the player class
// including static fields. The default class loader loads
// class from cache if it exists
class ClassReloader extends URLClassLoader {
	private ClassLoader parent;

	public ClassReloader(URL url, ClassLoader parent) {
		super(new URL[] { url }, null);
		this.parent = parent;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) {
		try {
			if ("piedpipers.sim.Player".equals(name)
					|| "piedpipers.sim.Point".equals(name))
				return parent.loadClass(name);
			else
				return super.loadClass(name, resolve);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}
}