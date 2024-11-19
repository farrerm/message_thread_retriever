package com.mycompany.app;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import okhttp3.*;

import java.time.Instant;

import java.sql.Connection;

public class RedditMessageThread {

    public String thread_id;
    public String subreddit;
    public String author;
    public String topic_complete;
    public String topic_summary;
    public String sentiment;
    public String is_open;
    public String timestamp;
    public String url;

    public List<JSONObject> messages = new ArrayList<>();

    RedditMessageThread(JSONArray raw_thread) {
        
        if (raw_thread.length() == 0) {
            throw new IllegalArgumentException("Thread is empty");
        }
        try {
            //construct message list
            for (int i = 0; i < raw_thread.length(); i++) {
                JSONObject subthread_json = raw_thread.getJSONObject(i);
                ArrayList<JSONObject> subthread = processSubthread(subthread_json);
                for (JSONObject message : subthread) {
                    this.messages.add(message);
                }
            }
        } catch (JSONException e) {
          // System.out.println(raw_thread.getJSONObject(0).toString(2));
          throw new RuntimeException("Error parsing thread");
            //e.printStackTrace();
        }
    }
    private static ArrayList<JSONObject> processSubthread(JSONObject message) {
        ArrayList<JSONObject> ret_val = new ArrayList<JSONObject>();
        try {
            String content = "";
            String author = "";
            String timestamp = "";
            String title = "";

            if (message.has("data")) {
                JSONObject data = message.getJSONObject("data");
                //check if this data is a comment
                //these seem to be the 2 cases where a json is a message
                if (data.has("selftext") || data.has("body")) {
                    //selftext and body are two diferent names for message contents.
                    if (data.has("selftext")){
                        content = data.getString("selftext");
                    }
                    else if (data.has("body")){
                        content = data.getString("body");
                    }
                    // content = data.getString("selftext");
                    author = data.getString("author");
                    timestamp = data.getString("created_utc");
                    JSONObject nu_json = new JSONObject();
                    if (data.has("title")){
                        nu_json.put("title", data.getString("title"));
                    }
                    nu_json.put("user", author);
                    nu_json.put("timestamp", timestamp);
                    nu_json.put("text", content);
                    ret_val.add(nu_json);
                }
                if (data.has("children")) {
                    JSONArray children = data.getJSONArray("children");
                    for (int i = 0; i < children.length(); i++) {
                        try {
                            JSONObject comment = children.getJSONObject(i);
                            ArrayList<JSONObject> temp = processSubthread(comment);
                            System.out.println("crashing here?");
                            for (JSONObject j : temp) {
                                ret_val.add(j);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                    }
                }
                if (data.has("replies") && data.get("replies") != "") {
                    try {
                        JSONObject reply = new JSONObject(data.get("replies").toString());
                        ArrayList<JSONObject> temp = processSubthread(reply);
                        for (JSONObject j : temp) {
                            ret_val.add(j);
                        }
                    }
                    catch (Exception e) {
                        return ret_val;
                    }
                }
            return ret_val;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
    
    public void computeTopicComplete() {
        try {
            String temp = "";
            temp += this.messages.get(0).getString("text");
            String author = this.messages.get(0).getString("user");

            //we will say that a topic is the text from 
            //the first message, plus any other messages
            //by the same author in the thread
            for (int i = 1; i < messages.size(); i++) {
                JSONObject message = messages.get(i);
                if (message.getString("user").equals(author)) {
                    temp += "\n" + message.getString("text");
                } 
            }
            this.topic_complete = temp;
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Error computing topic");
        } 
    }

    public String messagesToString() {
        try {
            String ret_val = "";
            ret_val += "[\n";
            for (JSONObject message : this.messages) {
                ret_val += message.toString(2) + "\n";
            }
            ret_val += "]\n";
            return ret_val;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String toString() {
        try {
            String ret_val = "";
            
            ret_val += "[\n";
            ret_val += "thread_id:\n";
            ret_val += this.thread_id + "\n";

            ret_val += "subreddit:\n";
            ret_val += this.subreddit + "\n";

            ret_val += "url:\n";
            ret_val += this.url + "\n";

            ret_val += "author:\n";
            ret_val += this.author + "\n";

            ret_val += "timestamp:\n";
            ret_val += this.timestamp + "\n";

            ret_val += "sentiment:\n";
            ret_val += this.sentiment + "\n";

            ret_val += "topic open?:\n";
            ret_val += this.is_open + "\n";

            ret_val += "topic summary:\n";
            ret_val += this.topic_summary + "\n";

            ret_val += "complete topic:\n";
            ret_val += this.topic_complete + "\n";

            for (JSONObject message : this.messages) {
                ret_val += message.toString(2) + "\n";
            }

            ret_val += "]\n";

            return ret_val;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
