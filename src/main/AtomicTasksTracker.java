package main;

import java.util.ArrayList;

import JsonObjects.AtomicTask;

public class AtomicTasksTracker {
	private ArrayList<AtomicTask>  tasks;
	private int done;
	
	public AtomicTasksTracker(ArrayList<AtomicTask> tasks, int done) {
		super();
		this.tasks = tasks;
		this.done = done;
	}
	
	public ArrayList<AtomicTask> getTasks() {
		return tasks;
	}
	
	public void setTasks(ArrayList<AtomicTask> tasks) {
		this.tasks = tasks;
	}
	
	public int getDone() {
		return done;
	}
	
	public void setDone(int done) {
		this.done = done;
	}
	
	public void incrementTaskDone() {
		this.done++;
	}
	
	public boolean isDone() {
		return (done == tasks.size());
	}

}
