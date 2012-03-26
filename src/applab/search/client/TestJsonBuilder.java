package applab.search.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import applab.client.Handset;
import applab.client.PropertyStorage;

public class TestJsonBuilder {
	
	public static void main(String[] args) {
			 
	        Map<String, Long> map = new HashMap<String, Long>();
	        map.put("A", 10L);
	        map.put("B", 20L);
	        map.put("C", 30L);
	 
	        JSONObject json = new JSONObject();
	        JSONObject json2 = new JSONObject();
	        try {
				json.accumulate("A", 10L);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	 
	        System.out.println(json.toString());
	 
	        List<String> list = new ArrayList<String>();
	        list.add("Sunday");
	        list.add("Monday");
	        list.add("Tuesday");
	        try {
				json.accumulate("weekdays", list);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        try {
				json2.accumulate("weekdays", list);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        System.out.println(json.toString());
	        System.out.println(json2.toString());
	    }
}
