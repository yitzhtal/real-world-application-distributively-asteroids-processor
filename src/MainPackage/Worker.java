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

import com.amazonaws.services.elasticbeanstalk.model.Queue;
import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;

import JsonObjects.AtomicAnalysis;
import JsonObjects.AtomicTask;
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
	
		//Properties properties = new Properties();
		//String path = "/AWSCredentials.properties";  //C:/Users/Tal Itshayek/Desktop/DistributedSystems/importexport-webservice-tool/AWSCredentials.properties
		//C:/Users/assaf/Downloads/AWSCredentials.properties
		/*try {
			properties.load(ClassLoader.getSystemResourceAsStream(path));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/
		
        Properties prop = new Properties();
        try {
            File jarPath = new File(Worker.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            String propertiesPath = jarPath.getParent();
            System.out.println("newPropetriesPath" + propertiesPath);
            File newJarPath = new File(propertiesPath);
            String newPropetriesPath = newJarPath.getParent();
            System.out.println("newPropetriesPath" + newPropetriesPath);
            prop.load(new FileInputStream(newPropetriesPath+Constants.path));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
		
		
	
		String accessKey = prop.getProperty("accessKeyId");
		String secretKey = prop.getProperty("secretKey"); 
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
				                StringBuilder HTMLOutput = new StringBuilder();
				      	    
				      	        String url = "https://api.nasa.gov/neo/rest/v1/feed?start_date="
				      	      		+ startDate
				      	      		+ "&end_date="
				      	      		+ endDate
				      	      		+ "&api_key=GG5T9i8vucmdDrRi3AgwU2aONZzLNHvos332Ch6a";
					     
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
					    	 			return;
					    	 	}	
					    }
	         }               
		}
	}
}
