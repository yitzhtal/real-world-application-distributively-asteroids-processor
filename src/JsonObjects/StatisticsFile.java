package JsonObjects;

public class StatisticsFile {
	private int asteroidsParsed;
	private int dangerousAsteroids;
	private int safeAsteroids;
	
	public StatisticsFile(int asteroidsParsed, int dangerousAsteroids, int safeAsteroids) {
		super();
		this.asteroidsParsed = asteroidsParsed;
		this.dangerousAsteroids = dangerousAsteroids;
		this.safeAsteroids = safeAsteroids;
	}

	public int getAsteroidsParsed() {
		return asteroidsParsed;
	}

	public void setAsteroidsParsed(int asteroidsParsed) {
		this.asteroidsParsed = asteroidsParsed;
	}

	public int getDangerousAsteroids() {
		return dangerousAsteroids;
	}

	public void setDangerousAsteroids(int dangerousAsteroids) {
		this.dangerousAsteroids = dangerousAsteroids;
	}

	public int getSafeAsteroids() {
		return safeAsteroids;
	}

	public void setSafeAsteroids(int safeAsteroids) {
		this.safeAsteroids = safeAsteroids;
	}


}
