package JsonObjects;

import org.json.JSONObject;

import enums.DangerColor;

public class AtomicAnalysis {
	private String nameAsteroid;
	private String close_approach_data;
	private Double velocity;
	private Double estimated_diameter_min;
	private Double estimated_diameter_max;
	private String miss_distance_kilometers;
	private DangerColor danger;
	
	public AtomicAnalysis(String nameAsteroid, String close_approach_data, Double velocity,
			Double estimated_diameter_min, Double estimated_diameter_max, String miss_distance_kilometers,
			DangerColor danger) {
		super();
		this.nameAsteroid = nameAsteroid;
		this.close_approach_data = close_approach_data;
		this.velocity = velocity;
		this.estimated_diameter_min = estimated_diameter_min;
		this.estimated_diameter_max = estimated_diameter_max;
		this.miss_distance_kilometers = miss_distance_kilometers;
		this.danger = danger;
	}
	
	public AtomicAnalysis() {
		super();
	}

	public String getNameAsteroid() {
		return nameAsteroid;
	}

	public void setNameAsteroid(String nameAsteroid) {
		this.nameAsteroid = nameAsteroid;
	}

	public String getClose_approach_data() {
		return close_approach_data;
	}

	public void setClose_approach_data(String close_approach_data) {
		this.close_approach_data = close_approach_data;
	}

	public Double getVelocity() {
		return velocity;
	}

	public void setVelocity(Double velocity) {
		this.velocity = velocity;
	}

	public Double getEstimated_diameter_min() {
		return estimated_diameter_min;
	}

	public void setEstimated_diameter_min(Double estimated_diameter_min) {
		this.estimated_diameter_min = estimated_diameter_min;
	}

	public Double getEstimated_diameter_max() {
		return estimated_diameter_max;
	}

	public void setEstimated_diameter_max(Double estimated_diameter_max) {
		this.estimated_diameter_max = estimated_diameter_max;
	}

	public String getMiss_distance_kilometers() {
		return miss_distance_kilometers;
	}

	public void setMiss_distance_kilometers(String miss_distance_kilometers) {
		this.miss_distance_kilometers = miss_distance_kilometers;
	}

	public DangerColor getDanger() {
		return danger;
	}

	public void setDanger(DangerColor danger) {
		this.danger = danger;
	}
	
	
}
