package MainPackage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;

import JsonObjects.AtomicAnalysis;
import JsonObjects.AtomicTask;
import JsonObjects.DangerColor;
import JsonObjects.TerminationMessage;
import JsonObjects.WorkerMessage;

public class Worker {
	
	private volatile static boolean terminated = false;
	
	public static void main(String[] args) throws Exception {
			
		/* credentials handling ...  */
	
		Properties properties = new Properties();
		String path = "C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties";  //"C:/Users/assaf/Downloads/AWSCredentials.properties";
		try {
			properties.load(new FileInputStream(path));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	
		String accessKey = properties.getProperty("accessKeyId");
		String secretKey = properties.getProperty("secretKey"); 
		mySQS.setAccessAndSecretKey(accessKey, secretKey);
	
		/* credentials handling ...  */
	
		while(!terminated) {	
			List<Message> result = mySQS.getInstance().awaitMessagesFromQueue(mySQS.getInstance().getQueueUrl(Manager.workersListener),10,"Worker");
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
					    if(w.getType().equals("AtomicTask")) {
					    	    task = new Gson().fromJson(w.getContent(), AtomicTask.class);  	
							    mySQS.getInstance().deleteMessageFromQueue(mySQS.getInstance().getQueueUrl(Manager.workersListener),msg); 
				                startDate = task.getStartDate();
				                endDate = task.getEndDate();
				                speedThreshold = task.getSpeedThreshold();
				                diameterThreshold = task.getDiameterThreshold();
				                missThreshold = task.getMissThreshold();
				                StringBuilder HTMLOutput = new StringBuilder();
				      	    
				      	        String url = "https://api.nasa.gov/neo/rest/v1/feed?start_date="
				      	      		+ startDate
				      	      		+ "&"
				      	      		+ endDate
				      	      		+ "=END_DATE&api_key=GG5T9i8vucmdDrRi3AgwU2aONZzLNHvos332Ch6a";
					     
				      			URL obj = new URL(url);
				      			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
				
				      			// optional default is GET
				      			con.setRequestMethod("GET");
				
				      			//add request header
				      			//con.setRequestProperty("api-key", API_KEY);
				
				      			int responseCode = con.getResponseCode();
				      			System.out.println("\nSending 'GET' request to URL : " + url);
				      			System.out.println("Response Code : " + responseCode);
				
				      			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				      			String inputLine;
				      			StringBuffer response = new StringBuffer();
				
				      			while ((inputLine = in.readLine()) != null) {
				      				response.append(inputLine);
				      			}
				      			in.close();
				
				      			JSONObject jsonObject = new JSONObject(new JSONTokener(response.toString()));
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
					      					
					      					if(isHazardous) {
						      					if(velocity >= speedThreshold) { //green
						      						danger = DangerColor.GREEN;
						      						System.out.println("Worker :: has changed danger to GREEN");
						      					}
						      					
						      					if(velocity >= speedThreshold && estimated_diameter_min >= diameterThreshold) { //yellow
						      						danger = DangerColor.YELLOW;
						      						System.out.println("Worker :: has changed danger to YELLOW");
						      					}
						      					
						      					if(velocity >= speedThreshold && estimated_diameter_min >= diameterThreshold && Double.parseDouble(miss_distance_kilometers) >= missThreshold) { //red
						      						danger = DangerColor.RED;
						      						System.out.println("Worker :: has changed danger to RED");
						      					} 
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
				    			System.out.println("----------------------------------------");
				    			System.out.println("----------------------------------------");
				    			System.out.println("----------------------------------------");
				      			System.out.println(analysisRes);
				    			System.out.println("----------------------------------------");
				    			System.out.println("----------------------------------------");
				    			System.out.println("----------------------------------------");
				      			mySQS.getInstance().sendMessageToQueue(Manager.managerListener,new Gson().toJson(task));					    		
					    }
					    
					    if(w.getType().equals("TerminationMessage")) {
					    	 	t = new Gson().fromJson(w.getContent(), TerminationMessage.class);  	
					    	 	if(t.isTerminate()) {
					    	 			System.out.println("----------------------------------------");
					    	 			System.out.println("I`m done working for today. gonna grab some beer :-)");
					    	 			System.out.println("----------------------------------------");
					    	 			return;
					    	 	}	
					    }
	         }               
		}
	}
}
