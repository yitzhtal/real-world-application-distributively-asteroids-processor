package MainPackage;



import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.ListIterator;

import javax.lang.model.element.Element;
import javax.swing.JTextPane;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.net.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TestNasaAPI {
	
	static String readFile(String path, Charset encoding) 
			  throws IOException 
			{
			  byte[] encoded = Files.readAllBytes(Paths.get(path));
			  return new String(encoded, encoding);
			}
	
	public static void main(String[] args) throws Exception {
	 
	    String templatePath = "AsteroidsAnalysis.html";

		File file = new File(templatePath);

		if(file.exists()) {
			System.out.println("File already exists");
		} else {
			FileWriter fileWriter = null;
			BufferedWriter bufferedWriter = null;
			fileWriter = new FileWriter(file);
			bufferedWriter = new BufferedWriter(fileWriter);	  		
			bufferedWriter.write(readFile("beginning.html",StandardCharsets.UTF_8));
			/* ...................................... */
			/* ......start the HTML body appending... */	
			/* ...................................... */
			
			String url = "https://api.nasa.gov/neo/rest/v1/feed?api_key=GG5T9i8vucmdDrRi3AgwU2aONZzLNHvos332Ch6a";

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

		
					if(velocity < 10 && isHazardous) { //green
						bufferedWriter.write("<tr bgcolor=#00ff00>");
					} else if(velocity > 10 && estimated_diameter_min < 200 && isHazardous) { //yellow
						bufferedWriter.write("<tr bgcolor=#FFff00>");
					} else if(velocity > 10 && estimated_diameter_max > 200 && Double.parseDouble(miss_distance_astronomical) < 0.3 && isHazardous) { //red
						bufferedWriter.write("<tr bgcolor=#FF0000>");
					} else {
						bufferedWriter.write("<tr>");
					}
					
					bufferedWriter.write("<td>" + nameAsteroid +"</td>");
					bufferedWriter.write("<td>" + close_approach_data_string +"</td>");
					bufferedWriter.write("<td>" + velocity +"</td>");
					bufferedWriter.write("<td>" + estimated_diameter_min +"</td>");
					bufferedWriter.write("<td>" + estimated_diameter_max+"</td>");
					bufferedWriter.write("<td>" + miss_distance_kilometers +"</td>");	
					bufferedWriter.write("</tr>");	
				}
				
			}  	
			
			/* ...................................... */			
			/* end the HTML body appending... */	
			/* ...................................... */

			bufferedWriter.write(readFile("end.html",StandardCharsets.UTF_8));
			System.out.println("Html was created!");
			bufferedWriter.flush();
			fileWriter.flush();
			bufferedWriter.close();
			fileWriter.close();
		}
		

}
}