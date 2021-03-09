package io.split.dbm.integrations.rudderstack2split;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.cloud.functions.HttpFunction;
import com.google.gson.Gson;

public class App implements HttpFunction
{

	public void service(com.google.cloud.functions.HttpRequest request, com.google.cloud.functions.HttpResponse response) throws Exception {
		long start = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		String splitApiKey = getFromHeaders(request, "Splitapikey");
		String trafficType = getFromHeaders(request, "Traffictype");
		String environmentName = getFromHeaders(request, "Environmentname");

		System.out.println("DEBUG - " + splitApiKey + " - " + trafficType + " - " + environmentName);
		
		StringWriter w = new StringWriter();
		IOUtils.copy(request.getInputStream(), w, Charset.forName("UTF-8"));
		JSONObject iObj = new JSONObject(w.toString());	
//		System.out.println(iObj.toString(2));

		RudderstackEvent rEvent = new Gson().fromJson(w.toString(), RudderstackEvent.class);
		System.out.println("created RudderstackEvent - " + rEvent.anonymousId + " - " + rEvent.event);

		JSONArray splitEvents = new JSONArray();
		String key = null;
		if(rEvent.userId != null && !rEvent.userId.isBlank()) {
			key = rEvent.userId;
		} else if (rEvent.anonymousId != null && !rEvent.anonymousId.isBlank()) {
			key = rEvent.anonymousId;
		} else {
			System.out.println("no key found in anonymous or user id");
		}
		if(key != null) {
			JSONObject splitEvent = new JSONObject();
			splitEvent.put("key", key);
			splitEvent.put("trafficTypeName", trafficType);
			String eventTypeId = cleanEventTypeId(rEvent.event);
			if(eventTypeId.isBlank()) {
				eventTypeId = rEvent.type;
			}
			splitEvent.put("eventTypeId", eventTypeId);
			splitEvent.put("value", 0);
			splitEvent.put("environmentName", environmentName);
			
			String timestamp = rEvent.originalTimestamp;
			splitEvent.put("timestamp", sdf.parse(timestamp).getTime());
			
			Map<String, Object> properties = new TreeMap<String, Object>();
			if(iObj.has("properties")) {
				putProperties(properties, "", iObj.getJSONObject("properties"));
			}
			if(iObj.has("context")) {
				putProperties(properties, "context.", iObj.getJSONObject("context"));
			}	
			properties.put("channel", rEvent.channel);
			properties.put("receivedAt", rEvent.receivedAt);
			properties.put("sentAt", rEvent.sentAt);
			properties.put("rudderId", rEvent.rudderId);
			properties.put("type", rEvent.type);
			
			splitEvent.put("properties", properties);
			splitEvents.put(splitEvent);
		}

		CreateEvents creator = new CreateEvents(splitApiKey, 100); // rudderstack sends one at time
		creator.doPost(splitEvents);

		PrintWriter writer = new PrintWriter(response.getWriter());
		writer.println("events posted to Split");
		writer.flush();
		writer.close();
		System.out.println("finished in " + (System.currentTimeMillis() - start) + "ms");
	}

	private String getFromHeaders(com.google.cloud.functions.HttpRequest request, String name) {
		String result = null;

		List<String> list = request.getHeaders().get(name);
//		System.out.println("DEBUG - " + request.getHeaders());
		if(list != null && !list.isEmpty()) {
			result = request.getHeaders().get(name).get(0);
		}

		return result;
	}

	private String cleanEventTypeId(String eventName) {
		String result = "";
		if(eventName != null) {
			char letter;
			for(int i = 0; i < eventName.length(); i++) {
				letter = eventName.charAt(i);
				if(!Character.isAlphabetic(letter)
						&& !Character.isDigit(letter)) {
					if(i == 0) {
						letter = '0';
					} else {
						if (letter != '-' && letter != '_' && letter != '.') {
							letter = '_';
						}
					}
				}
				result += "" + letter;
			}
		}
		return result;
	}
	
	private void putProperties(Map<String, Object> properties, String prefix, JSONObject obj) {
		for(String k : obj.keySet()) {
			if(obj.get(k) instanceof JSONArray) {
				JSONArray array = obj.getJSONArray(k);
				for(int j = 0; j < array.length(); j++) {
					putProperties(properties, prefix + k + ".", array.getJSONObject(j));
				}
			} else if (obj.get(k) instanceof JSONObject) {
				JSONObject o = obj.getJSONObject(k);
				for(String key : o.keySet()) {
					if(o.get(key) instanceof JSONObject) {
						JSONObject d = (JSONObject) o.get(key);
						putProperties(properties, prefix + key + ".", d);
					} else {
						properties.put(prefix + k + "." + key, o.get(key));
					}
				}
			} else {
				properties.put(prefix + k, obj.get(k));
			}
		}
	}
}