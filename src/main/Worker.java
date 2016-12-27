package main;

import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import java.net.URL;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.amazonaws.auth.PropertiesCredentials;

import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;

import JsonObjects.AtomicAnalysis;
import JsonObjects.AtomicTask;
import JsonObjects.StatisticsFile;
import JsonObjects.TerminationMessage;
import JsonObjects.WorkerMessage;
import enums.DangerColor;
import enums.WorkerMessageType;

public class Worker {
	
	private volatile static boolean terminated = false;
	//return true if currentEndDate is after endDate.
	
	private static int asteroidsParsed = 0;
	private static int dangerousAsteroids = 0; //Green/Yellow/Red
	private static int safeAsteroids = 0; //safe, non-hazardous asteroids

	public static void main(String[] args) throws Exception {
			
		/* credentials handling ...  */
		
		PropertiesCredentials p = new PropertiesCredentials(LocalApplication.class.getResourceAsStream("/" + Constants.AWSCredentialsProperties));
	    
		String accessKey = p.getAWSAccessKeyId();
		String secretKey = p.getAWSSecretKey();
		
		String currentAPIKey = "GG5T9i8vucmdDrRi3AgwU2aONZzLNHvos332Ch6a";
		
		mySQS.setAccessAndSecretKey(accessKey, secretKey);
	
		/* credentials handling ...  */
	
		while(!terminated) {	
			String workersListenerURL = mySQS.getInstance().getQueueUrl(Constants.workersListener);
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(workersListenerURL,Constants.WorkerAwaitMessageDelay,"Worker");
	        String startDate,endDate;
	        int speedThreshold,diameterThreshold;
	        double missThreshold;
	        AtomicTask task = null;
	        WorkerMessage w = null;
	        TerminationMessage t = null;
	        //Worker can get 2 kinds of messages: AtomicTask / TerminationMessage
			for(Message msg : result) { 
					    String s = msg.getBody(); 
					    w = new Gson().fromJson(s, WorkerMessage.class);
					    if(w.getType() == WorkerMessageType.AtomicTask) {
					    	    task = new Gson().fromJson(w.getContent(), AtomicTask.class);  
							    startDate = task.getStartDate();
				                endDate = task.getEndDate();
				                speedThreshold = task.getSpeedThreshold();
				                diameterThreshold = task.getDiameterThreshold();
				                missThreshold = task.getMissThreshold();
				                System.out.println("Worker :: now using api key = "+currentAPIKey);
				                String url = "https://api.nasa.gov/neo/rest/v1/feed?start_date="
				      	      		+ startDate
				      	      		+ "&end_date="
				      	      		+ endDate
				      	      		+ "&api_key=" + currentAPIKey;
					     
				                HttpClient client = HttpClientBuilder.create().build();
				        		HttpGet request = new HttpGet(url);
				        		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(3000).setConnectTimeout(3000).setConnectionRequestTimeout(3000).build();
				        		request.setConfig(requestConfig);
				        		request.addHeader("User-Agent", "Dist1");
				        		HttpResponse response = client.execute(request);

				        		int counter = 0;
				        		
				        		System.out.println("Worker :: status code = " + response.getStatusLine().getStatusCode());
				        		
				        		while(response.getStatusLine().getStatusCode() != 200 && counter < Constants.amountOfAttempsToReconnectToNasa){
					        			EntityUtils.consume(response.getEntity());
					        			System.out.println("Worker :: trying again to get HTTP again...");
					        			
					        			int randomNum = ThreadLocalRandom.current().nextInt(0, Constants.NasaAPICacheList.length);
					        			System.out.println("Worker :: random a nasa api key from the array in cell ->"+randomNum);
					        			currentAPIKey = Constants.NasaAPICacheList[randomNum];
					        			System.out.println("Worker :: let`s try another key? " + currentAPIKey);
					        			url = "https://api.nasa.gov/neo/rest/v1/feed?start_date="
								      	      		+ startDate
								      	      		+ "&end_date="
								      	      		+ endDate
								      	      		+ "&api_key="+
								      	      		currentAPIKey;
									     
								        client = HttpClientBuilder.create().build();
								        request = new HttpGet(url);
								        requestConfig = RequestConfig.custom().setSocketTimeout(3000).setConnectTimeout(3000).setConnectionRequestTimeout(3000).build();
								        		request.setConfig(requestConfig);
								        		request.addHeader("User-Agent", "Dist1");
					        			response = client.execute(request);
					        			counter++;
				        		}
				        		
				      			BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				      			String inputLine;
				      			StringBuffer responseBuffer = new StringBuffer();
				
				      			while ((inputLine = in.readLine()) != null) {
				      				responseBuffer.append(inputLine);
				      			}
				      			in.close();
				
				      			JSONObject jsonObject = new JSONObject(new JSONTokener(responseBuffer.toString()));
				      			JSONObject nearEarthObjects = jsonObject.getJSONObject("near_earth_objects");
				      			Iterator<?> nearEarthObjectsIterator = nearEarthObjects.keys();
				      			JSONArray ja = new JSONArray();

				      			while (nearEarthObjectsIterator.hasNext()) {
				      				String currentDate = nearEarthObjectsIterator.next().toString();
				      				JSONArray lineItems = nearEarthObjects.getJSONArray(currentDate);
				
				      				//Iterate all Asteroids on a specific Date
				      				
				      				for (int i = 0; i < lineItems.length(); i++) {
					      					JSONObject specificAsteroid = lineItems.getJSONObject(i);
					      					Boolean isHazardous = specificAsteroid.getBoolean("is_potentially_hazardous_asteroid");
					      					String nameAsteroid = specificAsteroid.getString("name");
					      					JSONObject close_approach_data = (JSONObject) specificAsteroid.getJSONArray("close_approach_data").get(0);  
					      					String close_approach_data_string = close_approach_data.getString("close_approach_date");
					      					Double velocity = close_approach_data.getJSONObject((("relative_velocity"))).getDouble("kilometers_per_second");
					      					Double estimated_diameter_min = specificAsteroid.getJSONObject("estimated_diameter").getJSONObject("meters").getDouble("estimated_diameter_min");
					      					Double estimated_diameter_max = specificAsteroid.getJSONObject("estimated_diameter").getJSONObject("meters").getDouble("estimated_diameter_max");			
					      					String miss_distance_kilometers = close_approach_data.getJSONObject("miss_distance").getString("kilometers");
					      					String miss_distance_astronomical = close_approach_data.getJSONObject("miss_distance").getString("astronomical");
			
					      					DangerColor danger = DangerColor.DEFAULT;
					      					asteroidsParsed++;
					      					//System.out.println("this asteroid "+nameAsteroid+ "is parsed right now");
					      					if(isHazardous) {    
							      					if(velocity >= speedThreshold) { //green
								      						danger = DangerColor.GREEN;
									      					if(estimated_diameter_min >= diameterThreshold) { //yellow
										      						danger = DangerColor.YELLOW;
										      						//System.out.println(Double.parseDouble(miss_distance_astronomical) + " >? " + missThreshold);
											      					if(Double.parseDouble(miss_distance_astronomical) >= missThreshold) { //red
											      						danger = DangerColor.RED;
											      					} 
									      					}
							      					}
					      					} else {
					      							//System.out.println("this asteroid is safe with color "+danger+", increasing dangerousAsteroids++");
					      							safeAsteroids++;
					      					}
					      					
					      					if(danger != DangerColor.DEFAULT) {
					      							//System.out.println("this asteroid is dangerous with color "+danger+". increasing dangerousAsteroids++");
					      							dangerousAsteroids++;
					      					}
					      					
					      					AtomicAnalysis analysis = new AtomicAnalysis(nameAsteroid,close_approach_data_string,velocity,
					      							estimated_diameter_min, estimated_diameter_max, miss_distance_kilometers,
					      							danger);
					      					
					      					ja.put(new Gson().toJson(analysis));	
				      				}
				      				
				      			}
				      			String analysisRes = ja.toString();		
				      			task.setDone(true);
				      			task.setAtomicAnalysisResult(analysisRes);
				      			mySQS.getInstance().sendMessageToQueue(Constants.managerListener,new Gson().toJson(task));

								mySQS.getInstance().deleteMessageFromQueue(mySQS.getInstance().getQueueUrl(Constants.workersListener),msg);

						}
					    
					    if(w.getType() == WorkerMessageType.TerminationMessage) {
					    	    mySQS.getInstance().deleteMessageFromQueue(mySQS.getInstance().getQueueUrl(Constants.workersListener),msg); 
					    	 	t = new Gson().fromJson(w.getContent(), TerminationMessage.class);  	
					    	 	if(t.isTerminate()) {
					    	 			System.out.println("----------------------------------------");
					    	 			System.out.println("I`m done working for today. gonna grab some beer :-)");
					    	 			System.out.println("----------------------------------------");
					    	 			System.out.println("------------Statistics-------------------");
					    	 			System.out.println("Asteroids Parsed: "+asteroidsParsed);
					    	 			System.out.println("Dangerous Asteroids (Green/Yellow/Red): "+dangerousAsteroids);
					    	 			System.out.println("Safe Asteroids: "+safeAsteroids);		
					    	 			System.out.println("----------------------------------------");
					    	 			System.out.println("----------------------------------------");
					    	 			
					    	 			StatisticsFile statisticsFile = new StatisticsFile(asteroidsParsed,dangerousAsteroids,safeAsteroids);
					    	 			mySQS.getInstance().sendMessageToQueue(mySQS.getInstance().getQueueUrl(Constants.statisticsData), new Gson().toJson(statisticsFile));
					    	 			return;
					    	 	}	
					    }
	         }               
		}
	}
}
